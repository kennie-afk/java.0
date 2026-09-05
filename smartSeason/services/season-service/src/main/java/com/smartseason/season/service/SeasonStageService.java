package com.smartseason.season.service;

import com.smartseason.season.domain.SeasonStage;
import com.smartseason.season.platform.EventPublisher;
import com.smartseason.season.platform.PageResponse;
import com.smartseason.season.platform.ResourceNotFoundException;
import com.smartseason.season.platform.TenantContext;
import com.smartseason.season.repo.SeasonStageRepository;
import com.smartseason.season.web.dto.SeasonStageCreateRequest;
import com.smartseason.season.web.dto.SeasonStageResponse;
import com.smartseason.season.web.dto.SeasonStageUpdateRequest;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class SeasonStageService {

    private static final String RESOURCE = "SeasonStage";

    private final SeasonStageRepository repository;
    private final EventPublisher events;

    public SeasonStageService(SeasonStageRepository repository, EventPublisher events) {
        this.repository = repository;
        this.events = events;
    }

    public PageResponse<SeasonStageResponse> list(Pageable pageable) {
        return PageResponse.from(
                repository.findAllByTenantId(TenantContext.requireTenantId(), pageable)
                        .map(SeasonStageResponse::from));
    }

    public SeasonStageResponse get(UUID id) {
        return SeasonStageResponse.from(require(id));
    }

    public long count() {
        return repository.countByTenantId(TenantContext.requireTenantId());
    }

    @Transactional
    public SeasonStageResponse create(SeasonStageCreateRequest request) {
        SeasonStage entity = new SeasonStage();
        entity.setTenantId(TenantContext.requireTenantId());
        entity.setSeasonId(request.seasonId());
        entity.setStageName(request.stageName());
        entity.setSequence(request.sequence());
        entity.setPlannedStart(request.plannedStart());
        entity.setPlannedEnd(request.plannedEnd());
        entity.setActualStart(request.actualStart());
        entity.setActualEnd(request.actualEnd());
        entity.setStatus(request.status());
        entity.setNotes(request.notes());

        SeasonStage saved = repository.save(entity);
        events.publish("farm", "SeasonStageCreated", saved.getId(), SeasonStageResponse.from(saved));
        return SeasonStageResponse.from(saved);
    }

    @Transactional
    public SeasonStageResponse update(UUID id, SeasonStageUpdateRequest request) {
        SeasonStage entity = require(id);
        if (request.seasonId() != null) {
            entity.setSeasonId(request.seasonId());
        }
        if (request.stageName() != null) {
            entity.setStageName(request.stageName());
        }
        if (request.sequence() != null) {
            entity.setSequence(request.sequence());
        }
        if (request.plannedStart() != null) {
            entity.setPlannedStart(request.plannedStart());
        }
        if (request.plannedEnd() != null) {
            entity.setPlannedEnd(request.plannedEnd());
        }
        if (request.actualStart() != null) {
            entity.setActualStart(request.actualStart());
        }
        if (request.actualEnd() != null) {
            entity.setActualEnd(request.actualEnd());
        }
        if (request.status() != null) {
            entity.setStatus(request.status());
        }
        if (request.notes() != null) {
            entity.setNotes(request.notes());
        }

        SeasonStage saved = repository.save(entity);
        events.publish("farm", "SeasonStageUpdated", saved.getId(), SeasonStageResponse.from(saved));
        return SeasonStageResponse.from(saved);
    }

    @Transactional
    public void delete(UUID id) {
        SeasonStage entity = require(id);
        repository.delete(entity);
        events.publish("farm", "SeasonStageDeleted", id, null);
    }

    private SeasonStage require(UUID id) {
        return repository.findByIdAndTenantId(id, TenantContext.requireTenantId())
                .orElseThrow(() -> new ResourceNotFoundException(RESOURCE, id));
    }
}
