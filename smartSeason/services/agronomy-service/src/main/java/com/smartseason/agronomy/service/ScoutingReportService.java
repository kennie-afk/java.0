package com.smartseason.agronomy.service;

import com.smartseason.agronomy.domain.ScoutingReport;
import com.smartseason.agronomy.platform.CountCache;
import com.smartseason.agronomy.platform.CountCache;
import com.smartseason.agronomy.platform.EventPublisher;
import com.smartseason.agronomy.platform.Cursor;
import com.smartseason.agronomy.platform.CursorPage;
import com.smartseason.agronomy.platform.PageResponse;
import com.smartseason.agronomy.platform.ResourceNotFoundException;
import com.smartseason.agronomy.platform.TenantContext;
import com.smartseason.agronomy.repo.ScoutingReportRepository;
import com.smartseason.agronomy.web.dto.ScoutingReportCreateRequest;
import com.smartseason.agronomy.web.dto.ScoutingReportResponse;
import com.smartseason.agronomy.web.dto.ScoutingReportUpdateRequest;
import com.smartseason.agronomy.platform.ListFilter;
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
public class ScoutingReportService {

    private static final String RESOURCE = "ScoutingReport";
    private static final String ENTITY = "scouting_reports";

    private static final Map<String, Class<?>> FILTERABLE = Map.ofEntries(
            Map.entry("plotId", UUID.class),
            Map.entry("seasonId", UUID.class),
            Map.entry("scoutedBy", UUID.class),
            Map.entry("pestDiseaseCode", String.class),
            Map.entry("photoUrl", String.class),
            Map.entry("status", ScoutingReport.Status.class));

    private static final List<String> SEARCHABLE = List.of("pestDiseaseCode", "photoUrl");

    private final ScoutingReportRepository repository;
    private final EventPublisher events;
    private final CountCache counts;

    public ScoutingReportService(ScoutingReportRepository repository, EventPublisher events, CountCache counts) {
        this.repository = repository;
        this.events = events;
        this.counts = counts;
    }

    public PageResponse<ScoutingReportResponse> list(Pageable pageable, Map<String, String> params) {
        if (ListFilter.isEmpty(params)) {
            return list(pageable);
        }
        UUID tenantId = TenantContext.requireTenantId();
        var spec = ListFilter.<ScoutingReport>of(tenantId, params, FILTERABLE, SEARCHABLE);
        return PageResponse.from(repository.findAll(spec, pageable).map(ScoutingReportResponse::from));
    }

    public PageResponse<ScoutingReportResponse> list(Pageable pageable) {
        UUID tenantId = TenantContext.requireTenantId();

        return PageResponse.of(
                repository.findAllByTenantId(tenantId, pageable).map(ScoutingReportResponse::from),
                counts.total(ENTITY, tenantId, () -> repository.countByTenantId(tenantId)));
    }

    public CursorPage<ScoutingReportResponse> listByCursor(String cursor, int size) {
        UUID tenantId = TenantContext.requireTenantId();

        Pageable pageable = PageRequest.of(0, Math.max(1, Math.min(size, 200)));

        Cursor from = Cursor.decode(cursor);
        Slice<ScoutingReport> slice = (from == null)
                ? repository.findAllByTenantIdOrderByCreatedAtDescIdDesc(tenantId, pageable)
                : repository.findAfterCursor(tenantId, from.createdAt(), from.id(), pageable);

        return CursorPage.of(
                slice,
                slice.getContent().stream().map(ScoutingReportResponse::from).toList(),
                e -> new Cursor(e.getCreatedAt(), e.getId()));
    }

    public ScoutingReportResponse get(UUID id) {
        return ScoutingReportResponse.from(require(id));
    }

    public long count() {
        return repository.countByTenantId(TenantContext.requireTenantId());
    }

    @Transactional
    public ScoutingReportResponse create(ScoutingReportCreateRequest request) {
        ScoutingReport entity = new ScoutingReport();
        entity.setTenantId(TenantContext.requireTenantId());
        entity.setPlotId(request.plotId());
        entity.setSeasonId(request.seasonId());
        entity.setScoutedBy(request.scoutedBy());
        entity.setScoutedAt(request.scoutedAt());
        entity.setPestDiseaseCode(request.pestDiseaseCode());
        entity.setIncidencePct(request.incidencePct());
        entity.setSeverityScore(request.severityScore());
        entity.setLatitude(request.latitude());
        entity.setLongitude(request.longitude());
        entity.setPhotoUrl(request.photoUrl());
        entity.setNotes(request.notes());
        entity.setStatus(request.status());

        ScoutingReport saved = repository.save(entity);
        counts.invalidate(ENTITY, saved.getTenantId());
        events.publish("farm", "ScoutingReportCreated", saved.getId(), ScoutingReportResponse.from(saved));
        return ScoutingReportResponse.from(saved);
    }

    @Transactional
    public ScoutingReportResponse update(UUID id, ScoutingReportUpdateRequest request) {
        ScoutingReport entity = require(id);
        if (request.plotId() != null) {
            entity.setPlotId(request.plotId());
        }
        if (request.seasonId() != null) {
            entity.setSeasonId(request.seasonId());
        }
        if (request.scoutedBy() != null) {
            entity.setScoutedBy(request.scoutedBy());
        }
        if (request.scoutedAt() != null) {
            entity.setScoutedAt(request.scoutedAt());
        }
        if (request.pestDiseaseCode() != null) {
            entity.setPestDiseaseCode(request.pestDiseaseCode());
        }
        if (request.incidencePct() != null) {
            entity.setIncidencePct(request.incidencePct());
        }
        if (request.severityScore() != null) {
            entity.setSeverityScore(request.severityScore());
        }
        if (request.latitude() != null) {
            entity.setLatitude(request.latitude());
        }
        if (request.longitude() != null) {
            entity.setLongitude(request.longitude());
        }
        if (request.photoUrl() != null) {
            entity.setPhotoUrl(request.photoUrl());
        }
        if (request.notes() != null) {
            entity.setNotes(request.notes());
        }
        if (request.status() != null) {
            entity.setStatus(request.status());
        }

        ScoutingReport saved = repository.save(entity);
        events.publish("farm", "ScoutingReportUpdated", saved.getId(), ScoutingReportResponse.from(saved));
        return ScoutingReportResponse.from(saved);
    }

    @Transactional
    public void delete(UUID id) {
        ScoutingReport entity = require(id);
        repository.delete(entity);
        counts.invalidate(ENTITY, entity.getTenantId());
        events.publish("farm", "ScoutingReportDeleted", id, null);
    }

    private ScoutingReport require(UUID id) {
        return repository.findByIdAndTenantId(id, TenantContext.requireTenantId())
                .orElseThrow(() -> new ResourceNotFoundException(RESOURCE, id));
    }
}
