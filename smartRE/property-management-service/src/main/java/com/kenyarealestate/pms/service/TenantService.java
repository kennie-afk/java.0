package com.kenyarealestate.pms.service;

import com.kenyarealestate.pms.client.NotificationClient;
import com.kenyarealestate.pms.client.UserLookupClient;
import com.kenyarealestate.pms.dto.*;
import com.kenyarealestate.pms.entity.Lease;
import com.kenyarealestate.pms.entity.LeaseStatus;
import com.kenyarealestate.pms.entity.Tenant;
import com.kenyarealestate.pms.exception.ConflictException;
import com.kenyarealestate.pms.exception.ForbiddenException;
import com.kenyarealestate.pms.exception.NotFoundException;
import com.kenyarealestate.pms.repository.LeaseRepository;
import com.kenyarealestate.pms.repository.TenantRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;

@Service
@Transactional
public class TenantService {

    private final TenantRepository tenants;
    private final LeaseRepository leases;
    private final UserLookupClient userLookup;
    private final NotificationClient notifications;

    @Value("${app.frontend-url}")
    private String frontendUrl;

    @Value("${app.invite.ttl-hours:72}")
    private int inviteTtlHours;

    /** 32 bytes from a CSPRNG. Guessing one is not a threat model anyone needs to model. */
    private static final SecureRandom RANDOM = new SecureRandom();

    public TenantService(TenantRepository tenants, LeaseRepository leases,
                         UserLookupClient userLookup, NotificationClient notifications) {
        this.tenants = tenants;
        this.leases = leases;
        this.userLookup = userLookup;
        this.notifications = notifications;
    }

    /**
     * Invites a tenant to connect their own account to this tenancy.
     *
     * <p>This is the consent step {@link #linkUser} does not have. Linking attaches whichever
     * account registered with the address the landlord recorded — but registration never
     * verified that address, so it really attaches whoever typed it first. An invitation
     * inverts that: the token only reaches the inbox the landlord recorded, and it is redeemed
     * by the tenant while signed in as themselves, so the connection becomes something they
     * did rather than something done to them.
     *
     * <p>Only the hash is stored. The plaintext exists for the length of this method and in
     * the one email; a token in the table would be a bearer credential for the tenancy sitting
     * in every backup of it.
     *
     * <p>Issuing a new invitation replaces any outstanding one, which is also how an
     * invitation is revoked: send another and the old link stops working.
     */
    public TenantResponse invite(UUID landlordId, UUID tenantId, String landlordName) {
        Tenant t = owned(landlordId, tenantId);

        if (t.getUserId() != null) {
            throw new ConflictException("This tenant is already connected to an account.");
        }
        if (!StringUtils.hasText(t.getEmail())) {
            throw new ConflictException(
                    "Add an email address to this tenant before inviting them.");
        }

        byte[] raw = new byte[32];
        RANDOM.nextBytes(raw);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(raw);

        t.setInviteTokenHash(sha256(token));
        t.setInviteExpiresAt(Instant.now().plus(inviteTtlHours, ChronoUnit.HOURS));
        t.setInviteSentAt(Instant.now());
        Tenant saved = tenants.save(t);

        String actionUrl = frontendUrl + "/register?invite=" + token;
        notifications.send(
                landlordId,
                "TENANT_INVITATION",
                saved.getEmail(),
                "tenant-invite:" + saved.getId() + ":" + saved.getInviteSentAt(),
                actionUrl,
                Map.of(
                        "landlordName", StringUtils.hasText(landlordName) ? landlordName : "Your landlord",
                        "unitLabel", unitLabelFor(saved),
                        "expiresInHours", String.valueOf(inviteTtlHours),
                        "actionUrl", actionUrl));

        return toResponse(saved);
    }

    /**
     * Connects the caller's own account to the tenancy an invitation names.
     *
     * <p>Takes the user id from the authenticated caller and never from the request. That is
     * the difference between "the tenant accepted" and "someone claimed on their behalf": the
     * token proves they received the landlord's email, and the session proves who they are.
     *
     * <p>Single use. The token is cleared on success, so a link forwarded afterwards connects
     * nobody.
     */
    public TenantResponse redeemInvite(UUID userId, String token) {
        if (!StringUtils.hasText(token)) {
            throw new ConflictException("This invitation link is incomplete.");
        }

        Tenant t = tenants.findByInviteTokenHash(sha256(token)).orElseThrow(() ->
                new ConflictException(
                        "This invitation is no longer valid. Ask your landlord to send another."));

        if (t.getUserId() != null) {
            throw new ConflictException("This tenancy is already connected to an account.");
        }
        if (t.getInviteExpiresAt() == null || t.getInviteExpiresAt().isBefore(Instant.now())) {
            throw new ConflictException(
                    "This invitation has expired. Ask your landlord to send another.");
        }

        t.setUserId(userId);
        t.setInviteTokenHash(null);
        t.setInviteExpiresAt(null);
        return toResponse(tenants.save(t));
    }

    private String unitLabelFor(Tenant t) {
        return leases.findFirstByTenantIdAndStatus(t.getId(), LeaseStatus.ACTIVE)
                .map(l -> "unit " + l.getUnitId())
                .orElse("your unit");
    }

    private static byte[] sha256(String value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is required and always present", e);
        }
    }

    public TenantResponse create(UUID landlordId, CreateTenantRequest req) {
        String phone = normalise(req.getPhone());
        tenants.findByLandlordIdAndPhone(landlordId, phone).ifPresent(t -> {
            throw new ConflictException("You already have a tenant on " + phone + " (" + t.getFullName() + ").");
        });

        return toResponse(tenants.save(Tenant.builder()
                .landlordId(landlordId)
                .fullName(req.getFullName().trim())
                .phone(phone)
                .email(StringUtils.hasText(req.getEmail()) ? req.getEmail().trim().toLowerCase() : null)
                .nationalId(req.getNationalId())
                .emergencyName(req.getEmergencyName())
                .emergencyPhone(req.getEmergencyPhone())
                .build()));
    }

    @Transactional(readOnly = true)
    public Page<TenantResponse> listMine(UUID landlordId, String query, Pageable pageable) {
        Page<Tenant> page = StringUtils.hasText(query)
                ? tenants.search(landlordId, query.trim(), pageable)
                : tenants.findByLandlordIdOrderByFullNameAsc(landlordId, pageable);
        return page.map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public TenantResponse get(UUID landlordId, UUID tenantId) {
        return toResponse(owned(landlordId, tenantId));
    }

    public TenantResponse update(UUID landlordId, UUID tenantId, UpdateTenantRequest req) {
        Tenant t = owned(landlordId, tenantId);

        if (StringUtils.hasText(req.getPhone())) {
            String phone = normalise(req.getPhone());
            if (!phone.equals(t.getPhone())) {
                tenants.findByLandlordIdAndPhone(landlordId, phone).ifPresent(other -> {
                    throw new ConflictException("Another of your tenants already uses " + phone + ".");
                });
                t.setPhone(phone);
            }
        }
        if (StringUtils.hasText(req.getFullName())) t.setFullName(req.getFullName().trim());
        if (req.getEmail() != null)          t.setEmail(StringUtils.hasText(req.getEmail()) ? req.getEmail().trim().toLowerCase() : null);
        if (req.getNationalId() != null)     t.setNationalId(req.getNationalId());
        if (req.getEmergencyName() != null)  t.setEmergencyName(req.getEmergencyName());
        if (req.getEmergencyPhone() != null) t.setEmergencyPhone(req.getEmergencyPhone());

        return toResponse(tenants.save(t));
    }

    /**
     * Connects a tenant record to the SmartRE account that owns its email address.
     *
     * <p>Takes no user id. The previous signature accepted one from the caller and set
     * it without checking anything, which let a landlord attach any account they could
     * name to a tenancy — that person would then see the landlord's invoices through
     * /my-tenancy and could raise maintenance against the unit. Resolving the id from
     * the address already on the record removes the choice: a landlord can only link
     * the person they already recorded as living there.
     *
     * <p>This is still landlord-asserted rather than tenant-accepted, which is a
     * weaker guarantee than an invitation the tenant confirms. It is bounded by the
     * landlord having had to know and record the address first.
     */
    public TenantResponse linkUser(UUID landlordId, UUID tenantId) {
        Tenant t = owned(landlordId, tenantId);

        if (t.getUserId() != null) {
            throw new ConflictException("This tenant is already linked to an account.");
        }
        if (t.getEmail() == null || t.getEmail().isBlank()) {
            throw new ConflictException(
                    "Add an email address to this tenant before linking their account.");
        }

        UUID userId = userLookup.findIdByEmail(t.getEmail()).orElseThrow(() ->
                new ConflictException(
                        "Nobody has registered with " + t.getEmail()
                        + ". Ask them to create a SmartRE account with that address first."));

        t.setUserId(userId);
        return toResponse(tenants.save(t));
    }

    /** Detaches the account, leaving the tenant record and its history intact. */
    public TenantResponse unlinkUser(UUID landlordId, UUID tenantId) {
        Tenant t = owned(landlordId, tenantId);
        t.setUserId(null);
        return toResponse(tenants.save(t));
    }

    public void delete(UUID landlordId, UUID tenantId) {
        Tenant t = owned(landlordId, tenantId);
        if (leases.existsByTenantId(tenantId)) {
            throw new ConflictException("This tenant has lease history and cannot be deleted.");
        }
        tenants.delete(t);
    }

    Tenant owned(UUID landlordId, UUID tenantId) {
        Tenant t = tenants.findById(tenantId).orElseThrow(() -> new NotFoundException("Tenant not found"));
        if (!t.getLandlordId().equals(landlordId)) {
            throw new ForbiddenException("This tenant belongs to another landlord.");
        }
        return t;
    }

    private String normalise(String phone) {
        String digits = phone.replaceAll("[^0-9+]", "");
        if (digits.startsWith("+")) digits = digits.substring(1);
        if (digits.startsWith("0") && digits.length() == 10) digits = "254" + digits.substring(1);
        return digits;
    }

    TenantResponse toResponse(Tenant t) {
        boolean active = leases.findByTenantIdInOrderByCreatedAtDesc(java.util.List.of(t.getId()), Pageable.unpaged())
                .stream().anyMatch(l -> l.getStatus() == LeaseStatus.ACTIVE);
        return TenantResponse.builder()
                .id(t.getId())
                .landlordId(t.getLandlordId())
                .userId(t.getUserId())
                // Only while one is outstanding. Cleared on redemption, so the landlord's
                // button goes back to reading "Invite" once the tenant has connected.
                .inviteSentAt(t.getInviteTokenHash() == null ? null : t.getInviteSentAt())
                .fullName(t.getFullName())
                .phone(t.getPhone())
                .email(t.getEmail())
                .nationalId(t.getNationalId())
                .emergencyName(t.getEmergencyName())
                .emergencyPhone(t.getEmergencyPhone())
                .hasActiveLease(active)
                .createdAt(t.getCreatedAt())
                .build();
    }
}
