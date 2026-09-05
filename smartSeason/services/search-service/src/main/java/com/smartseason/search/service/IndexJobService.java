package com.smartseason.search.service;

import com.smartseason.search.domain.IndexJob;
import com.smartseason.search.platform.EventPublisher;
import com.smartseason.search.platform.PageResponse;
import com.smartseason.search.platform.ResourceNotFoundException;
import com.smartseason.search.platform.TenantContext;
import com.smartseason.search.repo.IndexJobRepository;
import com.smartseason.search.web.dto.IndexJobCreateRequest;
import com.smartseason.search.web.dto.IndexJobResponse;
import com.smartseason.search.web.dto.IndexJobUpdateRequest;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class IndexJobService {

    private static final String RESOURCE = "IndexJob";

    private final IndexJobRepository repository;
    private final EventPublisher events;

    public IndexJobService(IndexJobRepository repository, EventPublisher events) {
        this.repository = repository;
        this.events = events;
    }

    public PageResponse<IndexJobResponse> list(Pageable pageable) {
        return PageResponse.from(
                repository.findAllByTenantId(TenantContext.requireTenantId(), pageable)
                        .map(IndexJobResponse::from));
    }

    public IndexJobResponse get(UUID id) {
        return IndexJobResponse.from(require(id));
    }

    public long count() {
        return repository.countByTenantId(TenantContext.requireTenantId());
    }

    @Transactional
    public IndexJobResponse create(IndexJobCreateRequest request) {
        IndexJob entity = new IndexJob();
        entity.setTenantId(TenantContext.requireTenantId());
        entity.setIndexName(request.indexName());
        entity.setJobType(request.jobType());
        entity.setSourceEvent(request.sourceEvent());
        entity.setDocumentsProcessed(request.documentsProcessed());
        entity.setStartedAt(request.startedAt());
        entity.setCompletedAt(request.completedAt());
        entity.setStatus(request.status());
        entity.setError(request.error());

        IndexJob saved = repository.save(entity);
        events.publish("platform", "IndexJobCreated", saved.getId(), IndexJobResponse.from(saved));
        return IndexJobResponse.from(saved);
    }

    @Transactional
    public IndexJobResponse update(UUID id, IndexJobUpdateRequest request) {
        IndexJob entity = require(id);
        if (request.indexName() != null) {
            entity.setIndexName(request.indexName());
        }
        if (request.jobType() != null) {
            entity.setJobType(request.jobType());
        }
        if (request.sourceEvent() != null) {
            entity.setSourceEvent(request.sourceEvent());
        }
        if (request.documentsProcessed() != null) {
            entity.setDocumentsProcessed(request.documentsProcessed());
        }
        if (request.startedAt() != null) {
            entity.setStartedAt(request.startedAt());
        }
        if (request.completedAt() != null) {
            entity.setCompletedAt(request.completedAt());
        }
        if (request.status() != null) {
            entity.setStatus(request.status());
        }
        if (request.error() != null) {
            entity.setError(request.error());
        }

        IndexJob saved = repository.save(entity);
        events.publish("platform", "IndexJobUpdated", saved.getId(), IndexJobResponse.from(saved));
        return IndexJobResponse.from(saved);
    }

    @Transactional
    public void delete(UUID id) {
        IndexJob entity = require(id);
        repository.delete(entity);
        events.publish("platform", "IndexJobDeleted", id, null);
    }

    private IndexJob require(UUID id) {
        return repository.findByIdAndTenantId(id, TenantContext.requireTenantId())
                .orElseThrow(() -> new ResourceNotFoundException(RESOURCE, id));
    }
}
