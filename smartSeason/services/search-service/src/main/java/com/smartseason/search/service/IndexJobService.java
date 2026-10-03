package com.smartseason.search.service;

import com.smartseason.search.domain.IndexJob;
import com.smartseason.search.platform.CountCache;
import com.smartseason.search.platform.CountCache;
import com.smartseason.search.platform.EventPublisher;
import com.smartseason.search.platform.Cursor;
import com.smartseason.search.platform.CursorPage;
import com.smartseason.search.platform.PageResponse;
import com.smartseason.search.platform.ResourceNotFoundException;
import com.smartseason.search.platform.TenantContext;
import com.smartseason.search.repo.IndexJobRepository;
import com.smartseason.search.web.dto.IndexJobCreateRequest;
import com.smartseason.search.web.dto.IndexJobResponse;
import com.smartseason.search.web.dto.IndexJobUpdateRequest;
import com.smartseason.search.platform.ListFilter;
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
public class IndexJobService {

    private static final String RESOURCE = "IndexJob";
    private static final String ENTITY = "index_jobs";

    private static final Map<String, Class<?>> FILTERABLE = Map.ofEntries(
            Map.entry("indexName", String.class),
            Map.entry("jobType", IndexJob.JobType.class),
            Map.entry("sourceEvent", String.class),
            Map.entry("status", IndexJob.Status.class));

    private static final List<String> SEARCHABLE = List.of("indexName", "sourceEvent");

    private final IndexJobRepository repository;
    private final EventPublisher events;
    private final CountCache counts;

    public IndexJobService(IndexJobRepository repository, EventPublisher events, CountCache counts) {
        this.repository = repository;
        this.events = events;
        this.counts = counts;
    }

    public PageResponse<IndexJobResponse> list(Pageable pageable, Map<String, String> params) {
        if (ListFilter.isEmpty(params)) {
            return list(pageable);
        }
        UUID tenantId = TenantContext.requireTenantId();
        var spec = ListFilter.<IndexJob>of(tenantId, params, FILTERABLE, SEARCHABLE);
        return PageResponse.from(repository.findAll(spec, pageable).map(IndexJobResponse::from));
    }

    public PageResponse<IndexJobResponse> list(Pageable pageable) {
        UUID tenantId = TenantContext.requireTenantId();

        return PageResponse.of(
                repository.findAllByTenantId(tenantId, pageable).map(IndexJobResponse::from),
                counts.total(ENTITY, tenantId, () -> repository.countByTenantId(tenantId)));
    }

    public CursorPage<IndexJobResponse> listByCursor(String cursor, int size) {
        UUID tenantId = TenantContext.requireTenantId();

        Pageable pageable = PageRequest.of(0, Math.max(1, Math.min(size, 200)));

        Cursor from = Cursor.decode(cursor);
        Slice<IndexJob> slice = (from == null)
                ? repository.findAllByTenantIdOrderByCreatedAtDescIdDesc(tenantId, pageable)
                : repository.findAfterCursor(tenantId, from.createdAt(), from.id(), pageable);

        return CursorPage.of(
                slice,
                slice.getContent().stream().map(IndexJobResponse::from).toList(),
                e -> new Cursor(e.getCreatedAt(), e.getId()));
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
        counts.invalidate(ENTITY, saved.getTenantId());
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
        counts.invalidate(ENTITY, entity.getTenantId());
        events.publish("platform", "IndexJobDeleted", id, null);
    }

    private IndexJob require(UUID id) {
        return repository.findByIdAndTenantId(id, TenantContext.requireTenantId())
                .orElseThrow(() -> new ResourceNotFoundException(RESOURCE, id));
    }
}
