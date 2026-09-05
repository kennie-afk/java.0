package com.smartseason.inventory.service;

import com.smartseason.inventory.domain.Reservation;
import com.smartseason.inventory.platform.EventPublisher;
import com.smartseason.inventory.platform.PageResponse;
import com.smartseason.inventory.platform.ResourceNotFoundException;
import com.smartseason.inventory.platform.TenantContext;
import com.smartseason.inventory.repo.ReservationRepository;
import com.smartseason.inventory.web.dto.ReservationCreateRequest;
import com.smartseason.inventory.web.dto.ReservationResponse;
import com.smartseason.inventory.web.dto.ReservationUpdateRequest;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class ReservationService {

    private static final String RESOURCE = "Reservation";

    private final ReservationRepository repository;
    private final EventPublisher events;

    public ReservationService(ReservationRepository repository, EventPublisher events) {
        this.repository = repository;
        this.events = events;
    }

    public PageResponse<ReservationResponse> list(Pageable pageable) {
        return PageResponse.from(
                repository.findAllByTenantId(TenantContext.requireTenantId(), pageable)
                        .map(ReservationResponse::from));
    }

    public ReservationResponse get(UUID id) {
        return ReservationResponse.from(require(id));
    }

    public long count() {
        return repository.countByTenantId(TenantContext.requireTenantId());
    }

    @Transactional
    public ReservationResponse create(ReservationCreateRequest request) {
        Reservation entity = new Reservation();
        entity.setTenantId(TenantContext.requireTenantId());
        entity.setStockItemId(request.stockItemId());
        entity.setOrderId(request.orderId());
        entity.setQuantity(request.quantity());
        entity.setReservedAt(request.reservedAt());
        entity.setExpiresAt(request.expiresAt());
        entity.setReleasedAt(request.releasedAt());
        entity.setStatus(request.status());

        Reservation saved = repository.save(entity);
        events.publish("market", "ReservationCreated", saved.getId(), ReservationResponse.from(saved));
        return ReservationResponse.from(saved);
    }

    @Transactional
    public ReservationResponse update(UUID id, ReservationUpdateRequest request) {
        Reservation entity = require(id);
        if (request.stockItemId() != null) {
            entity.setStockItemId(request.stockItemId());
        }
        if (request.orderId() != null) {
            entity.setOrderId(request.orderId());
        }
        if (request.quantity() != null) {
            entity.setQuantity(request.quantity());
        }
        if (request.reservedAt() != null) {
            entity.setReservedAt(request.reservedAt());
        }
        if (request.expiresAt() != null) {
            entity.setExpiresAt(request.expiresAt());
        }
        if (request.releasedAt() != null) {
            entity.setReleasedAt(request.releasedAt());
        }
        if (request.status() != null) {
            entity.setStatus(request.status());
        }

        Reservation saved = repository.save(entity);
        events.publish("market", "ReservationUpdated", saved.getId(), ReservationResponse.from(saved));
        return ReservationResponse.from(saved);
    }

    @Transactional
    public void delete(UUID id) {
        Reservation entity = require(id);
        repository.delete(entity);
        events.publish("market", "ReservationDeleted", id, null);
    }

    private Reservation require(UUID id) {
        return repository.findByIdAndTenantId(id, TenantContext.requireTenantId())
                .orElseThrow(() -> new ResourceNotFoundException(RESOURCE, id));
    }
}
