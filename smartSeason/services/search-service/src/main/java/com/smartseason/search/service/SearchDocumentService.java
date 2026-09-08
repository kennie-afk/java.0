package com.smartseason.search.service;

import com.smartseason.search.domain.SearchDocument;
import com.smartseason.search.platform.CountCache;
import com.smartseason.search.platform.CountCache;
import com.smartseason.search.platform.EventPublisher;
import com.smartseason.search.platform.PageResponse;
import com.smartseason.search.platform.ResourceNotFoundException;
import com.smartseason.search.platform.TenantContext;
import com.smartseason.search.repo.SearchDocumentRepository;
import com.smartseason.search.web.dto.SearchDocumentCreateRequest;
import com.smartseason.search.web.dto.SearchDocumentResponse;
import com.smartseason.search.web.dto.SearchDocumentUpdateRequest;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class SearchDocumentService {

    private static final String RESOURCE = "SearchDocument";
    private static final String ENTITY = "search_documents";

    private final SearchDocumentRepository repository;
    private final EventPublisher events;
    private final CountCache counts;

    public SearchDocumentService(SearchDocumentRepository repository, EventPublisher events, CountCache counts) {
        this.repository = repository;
        this.events = events;
        this.counts = counts;
    }

    public PageResponse<SearchDocumentResponse> list(Pageable pageable) {
        UUID tenantId = TenantContext.requireTenantId();

        return PageResponse.of(
                repository.findAllByTenantId(tenantId, pageable).map(SearchDocumentResponse::from),
                counts.total(ENTITY, tenantId, () -> repository.countByTenantId(tenantId)));
    }

    public SearchDocumentResponse get(UUID id) {
        return SearchDocumentResponse.from(require(id));
    }

    public long count() {
        return repository.countByTenantId(TenantContext.requireTenantId());
    }

    @Transactional
    public SearchDocumentResponse create(SearchDocumentCreateRequest request) {
        SearchDocument entity = new SearchDocument();
        entity.setTenantId(TenantContext.requireTenantId());
        entity.setIndexName(request.indexName());
        entity.setDocId(request.docId());
        entity.setDocType(request.docType());
        entity.setTitle(request.title());
        entity.setBody(request.body());
        entity.setKeywords(request.keywords());
        entity.setCounty(request.county());
        entity.setCommodityCode(request.commodityCode());
        entity.setLatitude(request.latitude());
        entity.setLongitude(request.longitude());
        entity.setBoost(request.boost());
        entity.setPayload(request.payload());
        entity.setIndexedAt(request.indexedAt());
        entity.setStatus(request.status());

        SearchDocument saved = repository.save(entity);
        counts.invalidate(ENTITY, saved.getTenantId());
        events.publish("platform", "SearchDocumentCreated", saved.getId(), SearchDocumentResponse.from(saved));
        return SearchDocumentResponse.from(saved);
    }

    @Transactional
    public SearchDocumentResponse update(UUID id, SearchDocumentUpdateRequest request) {
        SearchDocument entity = require(id);
        if (request.indexName() != null) {
            entity.setIndexName(request.indexName());
        }
        if (request.docId() != null) {
            entity.setDocId(request.docId());
        }
        if (request.docType() != null) {
            entity.setDocType(request.docType());
        }
        if (request.title() != null) {
            entity.setTitle(request.title());
        }
        if (request.body() != null) {
            entity.setBody(request.body());
        }
        if (request.keywords() != null) {
            entity.setKeywords(request.keywords());
        }
        if (request.county() != null) {
            entity.setCounty(request.county());
        }
        if (request.commodityCode() != null) {
            entity.setCommodityCode(request.commodityCode());
        }
        if (request.latitude() != null) {
            entity.setLatitude(request.latitude());
        }
        if (request.longitude() != null) {
            entity.setLongitude(request.longitude());
        }
        if (request.boost() != null) {
            entity.setBoost(request.boost());
        }
        if (request.payload() != null) {
            entity.setPayload(request.payload());
        }
        if (request.indexedAt() != null) {
            entity.setIndexedAt(request.indexedAt());
        }
        if (request.status() != null) {
            entity.setStatus(request.status());
        }

        SearchDocument saved = repository.save(entity);
        events.publish("platform", "SearchDocumentUpdated", saved.getId(), SearchDocumentResponse.from(saved));
        return SearchDocumentResponse.from(saved);
    }

    @Transactional
    public void delete(UUID id) {
        SearchDocument entity = require(id);
        repository.delete(entity);
        counts.invalidate(ENTITY, entity.getTenantId());
        events.publish("platform", "SearchDocumentDeleted", id, null);
    }

    private SearchDocument require(UUID id) {
        return repository.findByIdAndTenantId(id, TenantContext.requireTenantId())
                .orElseThrow(() -> new ResourceNotFoundException(RESOURCE, id));
    }
}
