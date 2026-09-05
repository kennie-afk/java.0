package com.smartseason.order.service;

import com.smartseason.order.domain.Dispute;
import com.smartseason.order.platform.EventPublisher;
import com.smartseason.order.platform.PageResponse;
import com.smartseason.order.platform.ResourceNotFoundException;
import com.smartseason.order.platform.TenantContext;
import com.smartseason.order.repo.DisputeRepository;
import com.smartseason.order.web.dto.DisputeCreateRequest;
import com.smartseason.order.web.dto.DisputeResponse;
import com.smartseason.order.web.dto.DisputeUpdateRequest;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class DisputeService {

    private static final String RESOURCE = "Dispute";

    private final DisputeRepository repository;
    private final EventPublisher events;

    public DisputeService(DisputeRepository repository, EventPublisher events) {
        this.repository = repository;
        this.events = events;
    }

    public PageResponse<DisputeResponse> list(Pageable pageable) {
        return PageResponse.from(
                repository.findAllByTenantId(TenantContext.requireTenantId(), pageable)
                        .map(DisputeResponse::from));
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
        events.publish("market", "DisputeDeleted", id, null);
    }

    private Dispute require(UUID id) {
        return repository.findByIdAndTenantId(id, TenantContext.requireTenantId())
                .orElseThrow(() -> new ResourceNotFoundException(RESOURCE, id));
    }
}
