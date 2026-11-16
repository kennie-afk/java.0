package com.smartseason.season.service;

import com.smartseason.season.domain.Season;
import com.smartseason.season.domain.SeasonStage;
import com.smartseason.season.domain.StageTemplate;
import com.smartseason.season.platform.DomainRuleException;
import com.smartseason.season.repo.SeasonStageRepository;
import com.smartseason.season.repo.StageTemplateRepository;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Locale;
import com.smartseason.season.platform.CountCache;
import com.smartseason.season.platform.CountCache;
import com.smartseason.season.platform.EventPublisher;
import com.smartseason.season.platform.ReferenceChecker;
import com.smartseason.season.platform.Cursor;
import com.smartseason.season.platform.CursorPage;
import com.smartseason.season.platform.PageResponse;
import com.smartseason.season.platform.ResourceNotFoundException;
import com.smartseason.season.platform.TenantContext;
import com.smartseason.season.repo.SeasonRepository;
import com.smartseason.season.web.dto.SeasonCreateRequest;
import com.smartseason.season.web.dto.SeasonResponse;
import com.smartseason.season.web.dto.SeasonUpdateRequest;
import com.smartseason.season.platform.ListFilter;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class SeasonService {

    private static final String RESOURCE = "Season";
    private static final String ENTITY = "seasons";

    private static final Map<String, Class<?>> FILTERABLE = Map.ofEntries(
            Map.entry("plotId", UUID.class),
            Map.entry("farmId", UUID.class),
            Map.entry("cropCode", String.class),
            Map.entry("variety", String.class),
            Map.entry("currentStage", String.class),
            Map.entry("status", Season.Status.class));

    private static final List<String> SEARCHABLE = List.of("cropCode", "variety", "currentStage");

    private final SeasonRepository repository;
    private final EventPublisher events;
    private final CountCache counts;
    private final ReferenceChecker references;
    private final SeasonStageRepository stages;
    private final StageTemplateRepository templates;

    public SeasonService(SeasonRepository repository, EventPublisher events, CountCache counts,
            ReferenceChecker references, SeasonStageRepository stages, StageTemplateRepository templates) {
        this.stages = stages;
        this.templates = templates;
        this.repository = repository;
        this.events = events;
        this.counts = counts;
        this.references = references;
    }

    public PageResponse<SeasonResponse> list(Pageable pageable, Map<String, String> params) {
        if (ListFilter.isEmpty(params)) {
            return list(pageable);
        }
        UUID tenantId = TenantContext.requireTenantId();
        var spec = ListFilter.<Season>of(tenantId, params, FILTERABLE, SEARCHABLE);
        return PageResponse.from(repository.findAll(spec, pageable).map(SeasonResponse::from));
    }

    public PageResponse<SeasonResponse> list(Pageable pageable) {
        UUID tenantId = TenantContext.requireTenantId();

        return PageResponse.of(
                repository.findAllByTenantId(tenantId, pageable).map(SeasonResponse::from),
                counts.total(ENTITY, tenantId, () -> repository.countByTenantId(tenantId)));
    }

    public CursorPage<SeasonResponse> listByCursor(String cursor, int size) {
        UUID tenantId = TenantContext.requireTenantId();

        Pageable pageable = PageRequest.of(0, Math.max(1, Math.min(size, 200)));

        Cursor from = Cursor.decode(cursor);
        Slice<Season> slice = (from == null)
                ? repository.findAllByTenantIdOrderByCreatedAtDescIdDesc(tenantId, pageable)
                : repository.findAfterCursor(tenantId, from.createdAt(), from.id(), pageable);

        return CursorPage.of(
                slice,
                slice.getContent().stream().map(SeasonResponse::from).toList(),
                e -> new Cursor(e.getCreatedAt(), e.getId()));
    }

    public SeasonResponse get(UUID id) {
        return SeasonResponse.from(require(id));
    }

    public long count() {
        return repository.countByTenantId(TenantContext.requireTenantId());
    }

    private static final ZoneId NAIROBI = ZoneId.of("Africa/Nairobi");

    /**
     * Starting a season. The caller says where, what and when; everything that follows from
     * that is worked out here: status, harvest date and the stage calendar. Harvest results
     * cannot be given at the start because they do not exist yet.
     */
    @Transactional
    public SeasonResponse create(SeasonCreateRequest request) {
        if (request.actualHarvestDate() != null || request.actualYieldKg() != null) {
            throw new DomainRuleException(
                    "Actual harvest date and yield are recorded when the season is harvested, not when it starts.");
        }
        LocalDate today = LocalDate.now(NAIROBI);
        UUID tenantId = TenantContext.requireTenantId();
        String crop = request.cropCode().trim().toUpperCase(Locale.ROOT);

        Season.Status status = request.status();
        if (status == null) {
            status = request.startDate().isAfter(today) ? Season.Status.PLANNED : Season.Status.ACTIVE;
        } else if (status != Season.Status.PLANNED && status != Season.Status.ACTIVE) {
            throw new DomainRuleException("A new season starts as Planned or Active.");
        }

        List<CropCalendar.Step> steps = calendarFor(crop, tenantId);
        int totalDays = steps.stream().mapToInt(CropCalendar.Step::days).sum();

        Season entity = new Season();
        entity.setTenantId(tenantId);
        entity.setPlotId(request.plotId());
        entity.setFarmId(request.farmId());
        entity.setCropCode(crop);
        entity.setVariety(request.variety());
        entity.setStartDate(request.startDate());
        entity.setExpectedHarvestDate(request.expectedHarvestDate() != null
                ? request.expectedHarvestDate()
                : totalDays > 0 ? request.startDate().plusDays(totalDays) : null);
        entity.setExpectedYieldKg(request.expectedYieldKg());
        entity.setStatus(status);

        List<SeasonStage> planned = new ArrayList<>();
        LocalDate cursor = request.startDate();
        int sequence = 1;
        String current = request.currentStage();
        boolean foundActive = false;
        for (CropCalendar.Step step : steps) {
            SeasonStage stage = new SeasonStage();
            stage.setTenantId(tenantId);
            stage.setStageName(step.name());
            stage.setSequence(sequence++);
            stage.setPlannedStart(cursor);
            stage.setPlannedEnd(cursor.plusDays(step.days()));
            cursor = cursor.plusDays(step.days());
            stage.setStatus(SeasonStage.Status.PENDING);
            if (status == Season.Status.ACTIVE) {
                if (!stage.getPlannedEnd().isAfter(today)) {
                    stage.setStatus(SeasonStage.Status.COMPLETE);
                    stage.setActualStart(stage.getPlannedStart());
                    stage.setActualEnd(stage.getPlannedEnd());
                } else if (!stage.getPlannedStart().isAfter(today) && !foundActive) {
                    stage.setStatus(SeasonStage.Status.ACTIVE);
                    stage.setActualStart(stage.getPlannedStart());
                    foundActive = true;
                    current = step.code();
                }
            }
            planned.add(stage);
        }
        if (!steps.isEmpty() && !foundActive) {
            // Planned: waiting on the first stage. Calendar already over: sitting on the last.
            current = (status == Season.Status.PLANNED ? steps.getFirst() : steps.getLast()).code();
        }
        entity.setCurrentStage(current);

        Season saved = repository.save(entity);
        for (SeasonStage stage : planned) {
            stage.setSeasonId(saved.getId());
            stages.save(stage);
        }
        counts.invalidate(ENTITY, saved.getTenantId());
        events.publish("farm", "SeasonCreated", saved.getId(), SeasonResponse.from(saved));
        return SeasonResponse.from(saved);
    }

    /** An organisation's own stage templates win; otherwise the built-in calendar for the crop. */
    private List<CropCalendar.Step> calendarFor(String crop, UUID tenantId) {
        List<StageTemplate> own = new ArrayList<>(
                templates.findAllByCropCodeAndTenantId(crop, tenantId, Pageable.unpaged()).getContent());
        if (own.isEmpty() || own.size() < 3) {
            // One stray template (the demo has a lone "Tasseling") is not a calendar.
            return CropCalendar.forCrop(crop);
        }
        own.sort(Comparator.comparing(StageTemplate::getSequence));
        return own.stream().map(t -> new CropCalendar.Step(t.getStageName(), t.getDurationDays())).toList();
    }

    private static boolean allowed(Season.Status from, Season.Status to) {
        if (from == to) {
            return true;
        }
        return switch (from) {
            case PLANNED -> to == Season.Status.ACTIVE || to == Season.Status.ABANDONED;
            case ACTIVE -> to == Season.Status.HARVESTED || to == Season.Status.ABANDONED;
            case HARVESTED -> to == Season.Status.CLOSED;
            default -> false;
        };
    }

    @Transactional
    public SeasonResponse update(UUID id, SeasonUpdateRequest request) {
        Season entity = require(id);
        if (request.plotId() != null) {
            entity.setPlotId(request.plotId());
        }
        if (request.farmId() != null) {
            entity.setFarmId(request.farmId());
        }
        if (request.cropCode() != null) {
            entity.setCropCode(request.cropCode());
        }
        if (request.variety() != null) {
            entity.setVariety(request.variety());
        }
        if (request.startDate() != null) {
            entity.setStartDate(request.startDate());
        }
        if (request.expectedHarvestDate() != null) {
            entity.setExpectedHarvestDate(request.expectedHarvestDate());
        }
        if (request.actualHarvestDate() != null) {
            entity.setActualHarvestDate(request.actualHarvestDate());
        }
        if (request.expectedYieldKg() != null) {
            entity.setExpectedYieldKg(request.expectedYieldKg());
        }
        if (request.actualYieldKg() != null) {
            entity.setActualYieldKg(request.actualYieldKg());
        }
        if (request.currentStage() != null) {
            entity.setCurrentStage(request.currentStage());
        }
        if (request.status() != null && request.status() != entity.getStatus()) {
            if (!allowed(entity.getStatus(), request.status())) {
                throw new DomainRuleException("A season cannot go from " + entity.getStatus()
                        + " to " + request.status() + ".");
            }
            if (request.status() == Season.Status.HARVESTED) {
                if (entity.getActualYieldKg() == null) {
                    throw new DomainRuleException("Record the actual yield to mark the season harvested.");
                }
                if (entity.getActualHarvestDate() == null) {
                    entity.setActualHarvestDate(LocalDate.now(NAIROBI));
                }
            }
            entity.setStatus(request.status());
        }

        Season saved = repository.save(entity);
        events.publish("farm", "SeasonUpdated", saved.getId(), SeasonResponse.from(saved));
        return SeasonResponse.from(saved);
    }

    @Transactional
    public void delete(UUID id) {
        Season entity = require(id);
        repository.delete(entity);
        counts.invalidate(ENTITY, entity.getTenantId());
        events.publish("farm", "SeasonDeleted", id, null);
    }

    private Season require(UUID id) {
        return repository.findByIdAndTenantId(id, TenantContext.requireTenantId())
                .orElseThrow(() -> new ResourceNotFoundException(RESOURCE, id));
    }
}
