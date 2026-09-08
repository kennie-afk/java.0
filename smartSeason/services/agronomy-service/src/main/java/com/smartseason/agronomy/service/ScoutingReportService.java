package com.smartseason.agronomy.service;

import com.smartseason.agronomy.domain.ScoutingReport;
import com.smartseason.agronomy.platform.CountCache;
import com.smartseason.agronomy.platform.CountCache;
import com.smartseason.agronomy.platform.EventPublisher;
import com.smartseason.agronomy.platform.PageResponse;
import com.smartseason.agronomy.platform.ResourceNotFoundException;
import com.smartseason.agronomy.platform.TenantContext;
import com.smartseason.agronomy.repo.ScoutingReportRepository;
import com.smartseason.agronomy.web.dto.ScoutingReportCreateRequest;
import com.smartseason.agronomy.web.dto.ScoutingReportResponse;
import com.smartseason.agronomy.web.dto.ScoutingReportUpdateRequest;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class ScoutingReportService {

    private static final String RESOURCE = "ScoutingReport";
    private static final String ENTITY = "scouting_reports";

    private final ScoutingReportRepository repository;
    private final EventPublisher events;
    private final CountCache counts;

    public ScoutingReportService(ScoutingReportRepository repository, EventPublisher events, CountCache counts) {
        this.repository = repository;
        this.events = events;
        this.counts = counts;
    }

    public PageResponse<ScoutingReportResponse> list(Pageable pageable) {
        UUID tenantId = TenantContext.requireTenantId();

        return PageResponse.of(
                repository.findAllByTenantId(tenantId, pageable).map(ScoutingReportResponse::from),
                counts.total(ENTITY, tenantId, () -> repository.countByTenantId(tenantId)));
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
