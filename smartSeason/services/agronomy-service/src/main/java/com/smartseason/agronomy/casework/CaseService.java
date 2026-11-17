package com.smartseason.agronomy.casework;

import com.smartseason.agronomy.casework.CaseDtos.*;
import com.smartseason.agronomy.domain.Advisory;
import com.smartseason.agronomy.platform.ConflictException;
import com.smartseason.agronomy.platform.DomainRuleException;
import com.smartseason.agronomy.platform.EventPublisher;
import com.smartseason.agronomy.platform.ResourceNotFoundException;
import com.smartseason.agronomy.platform.TenantContext;
import com.smartseason.agronomy.repo.AdvisoryRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The workflow between a farmer and an agronomist.
 *
 * The AI answers first, elsewhere. A case exists only when a person is needed, and it is sent to
 * the least-loaded agronomist who covers the county or, if nobody can take it, held in a shared
 * queue ordered by urgency. Everything that lists cases is keyset-paged and index-ordered, so
 * the cost of opening the queue does not grow with the number of cases ever raised.
 */
@Service
@Transactional
public class CaseService {

    static final int MAX_PHOTOS = 3;
    static final int MAX_PHOTO_BYTES = 900_000;
    private static final Set<String> IMAGE_TYPES = Set.of("image/jpeg", "image/png", "image/webp");
    private static final int MAX_PAGE = 50;

    public enum Scope { UNASSIGNED, MINE, ANSWERED }

    private final CaseRepository cases;
    private final CaseMessageRepository messages;
    private final CasePhotoRepository photos;
    private final AgronomistProfileRepository profiles;
    private final AdvisoryRepository advisories;
    private final IdentityDirectory identity;
    private final CaseMailer mailer;
    private final EventPublisher events;

    public CaseService(CaseRepository cases, CaseMessageRepository messages, CasePhotoRepository photos,
                       AgronomistProfileRepository profiles, AdvisoryRepository advisories,
                       IdentityDirectory identity, CaseMailer mailer, EventPublisher events) {
        this.cases = cases;
        this.messages = messages;
        this.photos = photos;
        this.profiles = profiles;
        this.advisories = advisories;
        this.identity = identity;
        this.mailer = mailer;
        this.events = events;
    }

    // ---- raising ------------------------------------------------------------------------

    public CaseDetail raise(Caller caller, RaiseRequest req) {
        UUID tenant = TenantContext.requireTenantId();
        List<byte[]> images = decode(req.photos());
        IdentityDirectory.Person me = identity.me(caller.id());

        AdvisoryCase c = new AdvisoryCase();
        c.setPlotId(req.plotId());
        c.setSeasonId(req.seasonId());
        c.setCropCode(blankToNull(req.cropCode() == null ? null : req.cropCode().trim().toUpperCase(Locale.ROOT)));
        c.setCounty(blankToNull(req.county()));
        c.setRaisedBy(caller.id());
        c.setRaisedByName(me.fullName());
        c.setRaisedByEmail(me.email());
        c.setQuestion(clean(req.question()));
        c.setReason(req.reason() == null ? AdvisoryCase.Reason.REQUESTED : req.reason());
        c.setAiSource(req.aiSource());
        c.setAiSummary(req.aiSummary() == null ? null : clean(req.aiSummary()));
        c.setAiConfidence(req.aiConfidence() == null ? null : Math.max(0, Math.min(1, req.aiConfidence())));
        c.setAiSeverity(req.aiSeverity());
        c.setPriority(CasePolicy.priorityFor(req.aiSeverity(), c.getReason()));
        Instant now = Instant.now();
        c.setDueAt(now.plus(CasePolicy.answerWithin(c.getPriority())));
        c.setStatus(AdvisoryCase.Status.OPEN);
        c.setAgronomistUnread(true);
        c.setPhotoCount(images.size());

        AgronomistProfile target = route(tenant, c.getCounty()).orElse(null);
        if (target != null) {
            c.setStatus(AdvisoryCase.Status.ASSIGNED);
            c.setAssignedTo(target.getUserId());
            c.setAssignedAt(now);
        }
        c = cases.save(c);

        for (int i = 0; i < images.size(); i++) {
            CasePhoto p = new CasePhoto();
            p.setCaseId(c.getId());
            p.setContentType(req.photos().get(i).mediaType().toLowerCase(Locale.ROOT));
            p.setSizeBytes(images.get(i).length);
            p.setData(images.get(i));
            photos.save(p);
        }
        events.publish("agronomy", "CaseRaised", c.getId(), Map.of("caseId", c.getId(),
                "priority", c.getPriority(), "assigned", target != null));

        if (target != null) {
            notifyAssignee(c, target.getEmail(), "New case: " + label(c), c.getQuestion());
        }
        return detail(caller, c, false);
    }

    private java.util.Optional<AgronomistProfile> route(UUID tenant, String county) {
        List<AgronomistProfile> active = profiles.findByTenantIdAndActiveTrue(tenant);
        if (active.isEmpty()) {
            return java.util.Optional.empty();
        }
        return CasePolicy.pick(active, loads(tenant), county);
    }

    // ---- reading ------------------------------------------------------------------------

    public CasePage queue(Caller caller, Scope scope, String cursor, int size) {
        requireStaff(caller);
        UUID tenant = TenantContext.requireTenantId();
        Pageable page = PageRequest.of(0, clampSize(size));
        CaseCursor from = CaseCursor.decode(cursor);
        Slice<AdvisoryCase> slice = switch (scope) {
            case UNASSIGNED -> from == null ? cases.openFirst(tenant, page)
                    : cases.openAfter(tenant, from.priority(), from.createdAt(), from.id(), page);
            case MINE -> from == null ? cases.assignedFirst(tenant, caller.id(), page)
                    : cases.assignedAfter(tenant, caller.id(), from.priority(), from.createdAt(), from.id(), page);
            case ANSWERED -> from == null ? cases.answeredFirst(tenant, caller.id(), page)
                    : cases.answeredAfter(tenant, caller.id(), from.priority(), from.createdAt(), from.id(), page);
        };
        return toPage(caller, slice, true);
    }

    /** A person's own questions; the owner and administrators may ask for the whole organisation. */
    public CasePage mine(Caller caller, boolean wholeOrganisation, String cursor, int size) {
        UUID tenant = TenantContext.requireTenantId();
        Pageable page = PageRequest.of(0, clampSize(size));
        CaseCursor from = CaseCursor.decode(cursor);
        boolean org = wholeOrganisation && (caller.isAdmin() || caller.has("FARMER"));
        Slice<AdvisoryCase> slice;
        if (org) {
            slice = from == null ? cases.findByTenantIdOrderByCreatedAtDescIdDesc(tenant, page)
                    : cases.orgAfter(tenant, from.createdAt(), from.id(), page);
        } else {
            slice = from == null
                    ? cases.findByTenantIdAndRaisedByOrderByCreatedAtDescIdDesc(tenant, caller.id(), page)
                    : cases.mineAfter(tenant, caller.id(), from.createdAt(), from.id(), page);
        }
        return toPage(caller, slice, false);
    }

    public CaseDetail get(Caller caller, UUID id) {
        AdvisoryCase c = visible(caller, id);
        boolean changed = false;
        if (c.getRaisedBy().equals(caller.id()) && c.isFarmerUnread()) {
            c.setFarmerUnread(false);
            changed = true;
        }
        if (caller.id().equals(c.getAssignedTo()) && c.isAgronomistUnread()) {
            c.setAgronomistUnread(false);
            changed = true;
        }
        if (changed) {
            c = cases.save(c);
        }
        return detail(caller, c, true);
    }

    @Transactional(readOnly = true)
    public CasePhoto photo(Caller caller, UUID caseId, UUID photoId) {
        visible(caller, caseId);
        return photos.findByIdAndTenantIdAndCaseId(photoId, TenantContext.requireTenantId(), caseId)
                .orElseThrow(() -> new ResourceNotFoundException("Photo", photoId));
    }

    @Transactional(readOnly = true)
    public Summary summary(Caller caller) {
        UUID tenant = TenantContext.requireTenantId();
        long attention = cases.countByTenantIdAndRaisedByAndFarmerUnreadTrue(tenant, caller.id())
                + cases.countUnreadAssignedTo(tenant, caller.id());
        if (!caller.isAgronomist() && !caller.isAdmin()) {
            return new Summary(0, 0, 0, attention);
        }
        return new Summary(
                cases.countOpen(tenant),
                cases.countAssignedTo(tenant, caller.id()),
                cases.countOverdue(tenant, Instant.now()),
                attention);
    }

    // ---- working a case -------------------------------------------------------------------

    public CaseDetail claim(Caller caller, UUID id) {
        requireStaff(caller);
        AdvisoryCase c = visible(caller, id);
        takeIfOpen(caller, c);
        c = reload(id);
        // Claiming is for taking an unassigned case. Someone who already has it gets it back
        // unchanged; anyone else is told it is taken (an administrator uses assign to move it).
        if (!caller.id().equals(c.getAssignedTo())) {
            throw new ConflictException("This case already belongs to another agronomist");
        }
        return detail(caller, c, true);
    }

    public CaseDetail assign(Caller caller, UUID id, UUID userId) {
        if (!caller.isAdmin()) {
            throw new DomainRuleException("Only an administrator can assign a case");
        }
        UUID tenant = TenantContext.requireTenantId();
        AgronomistProfile target = profiles.findByTenantIdAndUserId(tenant, userId)
                .orElseThrow(() -> new DomainRuleException("That person has not set up an agronomist profile yet"));
        AdvisoryCase c = visible(caller, id);
        if (c.getStatus() == AdvisoryCase.Status.CLOSED) {
            throw new DomainRuleException("This case is closed");
        }
        c.setAssignedTo(target.getUserId());
        c.setAssignedAt(Instant.now());
        c.setStatus(AdvisoryCase.Status.ASSIGNED);
        c.setAgronomistUnread(true);
        c = cases.save(c);
        notifyAssignee(c, target.getEmail(), "Case assigned to you: " + label(c), c.getQuestion());
        return detail(caller, c, false);
    }

    public CaseDetail reply(Caller caller, UUID id, String body) {
        AdvisoryCase c = visible(caller, id);
        if (c.getStatus() == AdvisoryCase.Status.CLOSED) {
            throw new DomainRuleException("This case is closed. Raise a new one if the problem is back.");
        }
        String text = clean(body);
        boolean fromRaiser = c.getRaisedBy().equals(caller.id());
        if (!fromRaiser && !(caller.isAgronomist() || caller.isAdmin())) {
            throw new ResourceNotFoundException("Case", id);
        }

        if (fromRaiser) {
            add(c, caller, text);
            if (c.getStatus() == AdvisoryCase.Status.ANSWERED) {
                c.setStatus(AdvisoryCase.Status.ASSIGNED);
                c.setDueAt(Instant.now().plus(CasePolicy.answerWithin(c.getPriority())));
            }
            c.setAgronomistUnread(true);
            c.setFarmerUnread(false);
            c = cases.save(c);
            if (c.getAssignedTo() != null) {
                String email = profiles.findByTenantIdAndUserId(c.getTenantId(), c.getAssignedTo())
                        .map(AgronomistProfile::getEmail).orElse(null);
                notifyAssignee(c, email, "Farmer replied: " + label(c), text);
            }
        } else {
            takeIfOpen(caller, c);
            c = reload(id);
            requireHolder(caller, c);
            add(c, caller, text);
            markAnswered(c);
            c = cases.save(c);
            notifyRaiser(c, "An agronomist replied to your question", text);
        }
        return detail(caller, c, true);
    }

    public CaseDetail resolve(Caller caller, UUID id, ResolveRequest req) {
        requireStaff(caller);
        AdvisoryCase c = visible(caller, id);
        if (c.getStatus() == AdvisoryCase.Status.CLOSED) {
            throw new DomainRuleException("This case is closed");
        }
        takeIfOpen(caller, c);
        c = reload(id);
        requireHolder(caller, c);

        Advisory a = new Advisory();
        a.setPlotId(c.getPlotId());
        a.setSeasonId(c.getSeasonId());
        a.setCropCode(c.getCropCode());
        a.setTitle(clean(req.title()));
        a.setBody(clean(req.body()));
        a.setSeverity(req.severity() == null ? Advisory.Severity.MEDIUM : req.severity());
        a.setSource(Advisory.Source.AGRONOMIST);
        a.setIssuedAt(Instant.now());
        a = advisories.save(a);
        events.publish("agronomy", "AdvisoryIssued", a.getId(), Map.of("advisoryId", a.getId(),
                "caseId", c.getId(), "severity", a.getSeverity().name()));

        add(c, caller, "Advisory issued: " + a.getTitle() + "\n\n" + a.getBody());
        c.setAdvisoryId(a.getId());
        markAnswered(c);
        c = cases.save(c);
        notifyRaiser(c, "Advice for your " + (c.getCropCode() == null ? "crop" : c.getCropCode().toLowerCase(Locale.ROOT)),
                a.getTitle() + "\n\n" + a.getBody());
        return detail(caller, c, true);
    }

    public CaseDetail close(Caller caller, UUID id) {
        AdvisoryCase c = visible(caller, id);
        boolean allowed = c.getRaisedBy().equals(caller.id()) || caller.isAgronomist() || caller.isAdmin();
        if (!allowed) {
            throw new ResourceNotFoundException("Case", id);
        }
        if (c.getStatus() != AdvisoryCase.Status.CLOSED) {
            c.setStatus(AdvisoryCase.Status.CLOSED);
            c.setClosedAt(Instant.now());
            c.setFarmerUnread(false);
            c.setAgronomistUnread(false);
            c = cases.save(c);
        }
        return detail(caller, c, false);
    }

    // ---- agronomist profiles --------------------------------------------------------------

    public ProfileView myProfile(Caller caller) {
        requireStaff(caller);
        UUID tenant = TenantContext.requireTenantId();
        IdentityDirectory.Person me = identity.me(caller.id());
        AgronomistProfile p = profiles.findByTenantIdAndUserId(tenant, caller.id()).orElseGet(() -> {
            AgronomistProfile fresh = new AgronomistProfile();
            fresh.setUserId(caller.id());
            return fresh;
        });
        boolean changed = p.getId() == null;
        if (me.email() != null && !me.email().equals(p.getEmail())) {
            p.setEmail(me.email());
            changed = true;
        }
        if (me.fullName() != null && !me.fullName().equals(p.getFullName())) {
            p.setFullName(me.fullName());
            changed = true;
        }
        if (changed) {
            p = profiles.save(p);
        }
        return profileView(p, loads(tenant));
    }

    public ProfileView updateProfile(Caller caller, ProfileRequest req) {
        requireStaff(caller);
        myProfile(caller);
        UUID tenant = TenantContext.requireTenantId();
        AgronomistProfile p = profiles.findByTenantIdAndUserId(tenant, caller.id()).orElseThrow();
        if (req.counties() != null) {
            p.setCounties(req.counties().stream().map(s -> s == null ? "" : s.trim().toLowerCase(Locale.ROOT))
                    .filter(s -> !s.isEmpty()).distinct().collect(Collectors.joining(",")));
        }
        if (req.maxOpen() != null) {
            if (req.maxOpen() < 1 || req.maxOpen() > 500) {
                throw new DomainRuleException("The limit must be between 1 and 500 open cases");
            }
            p.setMaxOpen(req.maxOpen());
        }
        if (req.active() != null) {
            p.setActive(req.active());
        }
        return profileView(profiles.save(p), loads(tenant));
    }

    @Transactional(readOnly = true)
    public List<ProfileView> allProfiles(Caller caller) {
        requireStaff(caller);
        UUID tenant = TenantContext.requireTenantId();
        Map<UUID, Long> load = loads(tenant);
        return profiles.findByTenantIdOrderByFullNameAsc(tenant).stream().map(p -> profileView(p, load)).toList();
    }

    // ---- internals ------------------------------------------------------------------------

    private void takeIfOpen(Caller caller, AdvisoryCase c) {
        if (c.getStatus() != AdvisoryCase.Status.OPEN) {
            return;
        }
        int won = cases.claim(c.getId(), TenantContext.requireTenantId(), caller.id(), Instant.now());
        if (won == 0) {
            AdvisoryCase now = reload(c.getId());
            if (!caller.id().equals(now.getAssignedTo()) && !caller.isAdmin()) {
                throw new ConflictException("Another agronomist has just taken this case");
            }
        }
    }

    private void requireHolder(Caller caller, AdvisoryCase c) {
        if (!caller.isAdmin() && !caller.id().equals(c.getAssignedTo())) {
            throw new ConflictException("This case belongs to another agronomist");
        }
    }

    private void requireStaff(Caller caller) {
        if (!caller.isAgronomist() && !caller.isAdmin()) {
            throw new ResourceNotFoundException("Case", new UUID(0, 0));
        }
    }

    private AdvisoryCase reload(UUID id) {
        return cases.findByIdAndTenantId(id, TenantContext.requireTenantId())
                .orElseThrow(() -> new ResourceNotFoundException("Case", id));
    }

    /** Not found rather than forbidden: whether someone else's case exists is not for the caller to learn. */
    private AdvisoryCase visible(Caller caller, UUID id) {
        AdvisoryCase c = reload(id);
        boolean ok = c.getRaisedBy().equals(caller.id()) || caller.id().equals(c.getAssignedTo())
                || caller.isAdmin() || caller.isAgronomist() || caller.has("FARMER");
        if (!ok) {
            throw new ResourceNotFoundException("Case", id);
        }
        return c;
    }

    private void add(AdvisoryCase c, Caller caller, String body) {
        CaseMessage m = new CaseMessage();
        m.setCaseId(c.getId());
        m.setAuthorId(caller.id());
        m.setAuthorName(identity.me(caller.id()).fullName());
        m.setAuthorRole(caller.displayRole());
        m.setBody(body);
        messages.save(m);
    }

    private void markAnswered(AdvisoryCase c) {
        c.setStatus(AdvisoryCase.Status.ANSWERED);
        c.setAnsweredAt(Instant.now());
        c.setFarmerUnread(true);
        c.setAgronomistUnread(false);
    }

    private Map<UUID, Long> loads(UUID tenant) {
        Map<UUID, Long> load = new HashMap<>();
        for (Object[] row : cases.openByAssignee(tenant)) {
            load.put(asUuid(row[0]), ((Number) row[1]).longValue());
        }
        return load;
    }

    /** Native queries return a UUID column as a UUID on Postgres but as bytes on H2. */
    static UUID asUuid(Object value) {
        if (value instanceof UUID u) {
            return u;
        }
        if (value instanceof byte[] b && b.length == 16) {
            java.nio.ByteBuffer buf = java.nio.ByteBuffer.wrap(b);
            return new UUID(buf.getLong(), buf.getLong());
        }
        return UUID.fromString(String.valueOf(value));
    }

    private static ProfileView profileView(AgronomistProfile p, Map<UUID, Long> load) {
        return new ProfileView(p.getUserId(), p.getFullName(), p.getEmail(), p.countyList(), p.getMaxOpen(),
                p.isActive(), load.getOrDefault(p.getUserId(), 0L));
    }

    private CasePage toPage(Caller caller, Slice<AdvisoryCase> slice, boolean staffView) {
        List<AdvisoryCase> rows = slice.getContent();
        Map<UUID, String> names = assigneeNames(rows);
        List<CaseView> items = rows.stream().map(c -> view(caller, c, names.get(c.getAssignedTo()), true)).toList();
        String next = slice.hasNext() && !rows.isEmpty() ? CaseCursor.of(rows.get(rows.size() - 1)).encode() : null;
        return new CasePage(items, next);
    }

    private Map<UUID, String> assigneeNames(List<AdvisoryCase> rows) {
        Set<UUID> ids = rows.stream().map(AdvisoryCase::getAssignedTo).filter(java.util.Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Map<UUID, String> out = new HashMap<>();
        if (!ids.isEmpty()) {
            for (AgronomistProfile p : profiles.findByTenantIdAndUserIdIn(TenantContext.requireTenantId(), ids)) {
                out.put(p.getUserId(), p.getFullName());
            }
        }
        return out;
    }

    private CaseView view(Caller caller, AdvisoryCase c, String assigneeName, boolean shorten) {
        boolean open = c.getStatus() == AdvisoryCase.Status.OPEN || c.getStatus() == AdvisoryCase.Status.ASSIGNED;
        boolean unread = (c.getRaisedBy().equals(caller.id()) && c.isFarmerUnread())
                || (caller.id().equals(c.getAssignedTo()) && c.isAgronomistUnread());
        String q = shorten && c.getQuestion().length() > 200 ? c.getQuestion().substring(0, 200) + "…" : c.getQuestion();
        return new CaseView(c.getId(), c.getCropCode(), c.getCounty(), q, c.getStatus(), c.getPriority(),
                open && c.getDueAt().isBefore(Instant.now()), c.getDueAt(), c.getCreatedAt(), c.getRaisedByName(),
                c.getAssignedTo(), assigneeName, c.getAiSource(), c.getAiConfidence(), c.getAiSeverity(),
                c.getReason(), c.getPhotoCount(), unread);
    }

    private CaseDetail detail(Caller caller, AdvisoryCase c, boolean withThread) {
        UUID tenant = TenantContext.requireTenantId();
        String assignee = c.getAssignedTo() == null ? null
                : profiles.findByTenantIdAndUserId(tenant, c.getAssignedTo()).map(AgronomistProfile::getFullName).orElse(null);
        List<MessageView> thread = new ArrayList<>();
        List<PhotoView> pics = new ArrayList<>();
        long similar = 0;
        if (withThread) {
            for (CaseMessage m : messages.findByTenantIdAndCaseIdOrderByCreatedAtAscIdAsc(tenant, c.getId(),
                    PageRequest.of(0, 200))) {
                thread.add(new MessageView(m.getId(), m.getAuthorId(), m.getAuthorName(), m.getAuthorRole(),
                        m.getBody(), m.getCreatedAt()));
            }
            for (Object[] row : photos.describe(tenant, c.getId())) {
                pics.add(new PhotoView(asUuid(row[0]), (String) row[1], ((Number) row[2]).intValue()));
            }
            if (c.getCounty() != null && c.getCropCode() != null && (caller.isAgronomist() || caller.isAdmin())) {
                similar = cases.countSimilarOpen(tenant, c.getId(), c.getCounty(), c.getCropCode());
            }
        }
        boolean staff = caller.isAgronomist() || caller.isAdmin();
        boolean openish = c.getStatus() == AdvisoryCase.Status.OPEN || c.getStatus() == AdvisoryCase.Status.ASSIGNED;
        boolean holder = caller.isAdmin() || caller.id().equals(c.getAssignedTo());
        boolean canReply = c.getStatus() != AdvisoryCase.Status.CLOSED
                && (c.getRaisedBy().equals(caller.id()) || (staff && (c.getAssignedTo() == null || holder)));
        return new CaseDetail(view(caller, c, assignee, false), c.getQuestion(), c.getAiSummary(), c.getPlotId(),
                c.getSeasonId(), c.getAdvisoryId(), similar, thread, pics,
                staff && c.getStatus() == AdvisoryCase.Status.OPEN,
                canReply, staff && openish && (c.getAssignedTo() == null || holder));
    }

    private void notifyAssignee(AdvisoryCase c, String email, String subject, String text) {
        mailer.send(email, subject, snippet(text) + "\n\nOpen the case: " + mailer.caseLink(c.getId()));
    }

    private void notifyRaiser(AdvisoryCase c, String subject, String text) {
        mailer.send(c.getRaisedByEmail(), subject, snippet(text) + "\n\nOpen the conversation: " + mailer.caseLink(c.getId()));
    }

    private static String snippet(String s) {
        String t = s == null ? "" : s.trim();
        return t.length() > 800 ? t.substring(0, 800) + "…" : t;
    }

    private static String label(AdvisoryCase c) {
        String crop = c.getCropCode() == null ? "crop" : c.getCropCode().toLowerCase(Locale.ROOT);
        return crop + (c.getCounty() == null ? "" : " in " + c.getCounty());
    }

    private static List<byte[]> decode(List<PhotoIn> photos) {
        List<byte[]> out = new ArrayList<>();
        if (photos == null) {
            return out;
        }
        if (photos.size() > MAX_PHOTOS) {
            throw new DomainRuleException("At most " + MAX_PHOTOS + " photographs per case");
        }
        for (PhotoIn p : photos) {
            String type = p.mediaType() == null ? "" : p.mediaType().toLowerCase(Locale.ROOT);
            if (!IMAGE_TYPES.contains(type)) {
                throw new DomainRuleException("Photographs must be JPEG, PNG or WebP");
            }
            byte[] bytes;
            try {
                bytes = Base64.getDecoder().decode(p.base64());
            } catch (IllegalArgumentException ex) {
                throw new DomainRuleException("A photograph could not be read");
            }
            if (bytes.length > MAX_PHOTO_BYTES) {
                throw new DomainRuleException("Each photograph must be under " + MAX_PHOTO_BYTES / 1000 + " KB");
            }
            out.add(bytes);
        }
        return out;
    }

    private static String clean(String s) {
        return s == null ? "" : s.replace("\u0000", "").trim();
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    private static int clampSize(int size) {
        return Math.max(1, Math.min(size, MAX_PAGE));
    }
}
