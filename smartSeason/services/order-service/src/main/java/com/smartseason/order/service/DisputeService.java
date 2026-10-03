package com.smartseason.order.service;

import com.smartseason.order.domain.Dispute;
import com.smartseason.order.platform.CountCache;
import com.smartseason.order.platform.CountCache;
import com.smartseason.order.platform.EventPublisher;
import com.smartseason.order.platform.Cursor;
import com.smartseason.order.platform.CursorPage;
import com.smartseason.order.platform.PageResponse;
import com.smartseason.order.platform.ResourceNotFoundException;
import com.smartseason.order.platform.TenantContext;
import com.smartseason.order.repo.DisputeRepository;
import com.smartseason.order.web.dto.DisputeCreateRequest;
import com.smartseason.order.web.dto.DisputeResponse;
import com.smartseason.order.web.dto.DisputeUpdateRequest;
import com.smartseason.order.platform.ListFilter;
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
public class DisputeService {

    private static final String RESOURCE = "Dispute";
    private static final String ENTITY = "disputes";

    private static final Map<String, Class<?>> FILTERABLE = Map.ofEntries(
            Map.entry("orderId", UUID.class),
            Map.entry("raisedByOrgId", UUID.class),
            Map.entry("category", Dispute.Category.class),
            Map.entry("status", Dispute.Status.class),
            Map.entry("resolvedBy", UUID.class));

    private static final List<String> SEARCHABLE = List.of();

    private final DisputeRepository repository;
    private final EventPublisher events;
    private final CountCache counts;

    public DisputeService(DisputeRepository repository, EventPublisher events, CountCache counts) {
        this.repository = repository;
        this.events = events;
        this.counts = counts;
    }

    public PageResponse<DisputeResponse> list(Pageable pageable, Map<String, String> params) {
        if (ListFilter.isEmpty(params)) {
            return list(pageable);
        }
        UUID tenantId = TenantContext.requireTenantId();
        var spec = ListFilter.<Dispute>of(tenantId, params, FILTERABLE, SEARCHABLE);
        return PageResponse.from(repository.findAll(spec, pageable).map(DisputeResponse::from));
    }

    public PageResponse<DisputeResponse> list(Pageable pageable) {
        UUID tenantId = TenantContext.requireTenantId();

        return PageResponse.of(
                repository.findAllByTenantId(tenantId, pageable).map(DisputeResponse::from),
                counts.total(ENTITY, tenantId, () -> repository.countByTenantId(tenantId)));
    }

    public CursorPage<DisputeResponse> listByCursor(String cursor, int size) {
        UUID tenantId = TenantContext.requireTenantId();

        Pageable pageable = PageRequest.of(0, Math.max(1, Math.min(size, 200)));

        Cursor from = Cursor.decode(cursor);
        Slice<Dispute> slice = (from == null)
                ? repository.findAllByTenantIdOrderByCreatedAtDescIdDesc(tenantId, pageable)
                : repository.findAfterCursor(tenantId, from.createdAt(), from.id(), pageable);

        return CursorPage.of(
                slice,
                slice.getContent().stream().map(DisputeResponse::from).toList(),
                e -> new Cursor(e.getCreatedAt(), e.getId()));
    }

    public DisputeResponse get(UUID id) {
        return DisputeResponse.from(require(id));
    }

    public long count() {
        return repository.countByTenantId(TenantContext.requireTenantId());
    }

    @Transactional
    public DisputeResponse create(DisputeCreateRequest request) {
        Dispute entity = new Dispute();
        entity.setTenantId(TenantContext.requireTenantId());
        entity.setOrderId(request.orderId());
        entity.setRaisedByOrgId(request.raisedByOrgId());
        entity.setCategory(request.category());
        entity.setDescription(request.description());
        entity.setRaisedAt(request.raisedAt());
        entity.setStatus(request.status());
        entity.setResolution(request.resolution());
        entity.setResolvedAt(request.resolvedAt());
        entity.setResolvedBy(request.resolvedBy());

        Dispute saved = repository.save(entity);
        counts.invalidate(ENTITY, saved.getTenantId());
        events.publish("market", "DisputeCreated", saved.getId(), DisputeResponse.from(saved));
        return DisputeResponse.from(saved);
    }

    @Transactional
    public DisputeResponse update(UUID id, DisputeUpdateRequest request) {
        Dispute entity = require(id);
        if (request.orderId() != null) {
            entity.setOrderId(request.orderId());
        }
        if (request.raisedByOrgId() != null) {
            entity.setRaisedByOrgId(request.raisedByOrgId());
        }
        if (request.category() != null) {
            entity.setCategory(request.category());
        }
        if (request.description() != null) {
            entity.setDescription(request.description());
        }
        if (request.raisedAt() != null) {
            entity.setRaisedAt(request.raisedAt());
        }
        if (request.status() != null) {
            entity.setStatus(request.status());
        }
        if (request.resolution() != null) {
            entity.setResolution(request.resolution());
        }
        if (request.resolvedAt() != null) {
            entity.setResolvedAt(request.resolvedAt());
        }
        if (request.resolvedBy() != null) {
            entity.setResolvedBy(request.resolvedBy());
        }

        Dispute saved = repository.save(entity);
        events.publish("market", "DisputeUpdated", saved.getId(), DisputeResponse.from(saved));
        return DisputeResponse.from(saved);
    }

    @Transactional
    public void delete(UUID id) {
        Dispute entity = require(id);
        repository.delete(entity);
        counts.invalidate(ENTITY, entity.getTenantId());
        events.publish("market", "DisputeDeleted", id, null);
    }

    private Dispute require(UUID id) {
        return repository.findByIdAndTenantId(id, TenantContext.requireTenantId())
                .orElseThrow(() -> new ResourceNotFoundException(RESOURCE, id));
    }
}
