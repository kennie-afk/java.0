package com.smartseason.inventory.service;

import com.smartseason.inventory.domain.Reservation;
import com.smartseason.inventory.platform.CountCache;
import com.smartseason.inventory.platform.CountCache;
import com.smartseason.inventory.platform.EventPublisher;
import com.smartseason.inventory.platform.ReferenceChecker;
import com.smartseason.inventory.platform.Cursor;
import com.smartseason.inventory.platform.CursorPage;
import com.smartseason.inventory.platform.PageResponse;
import com.smartseason.inventory.platform.ResourceNotFoundException;
import com.smartseason.inventory.platform.TenantContext;
import com.smartseason.inventory.repo.ReservationRepository;
import com.smartseason.inventory.web.dto.ReservationCreateRequest;
import com.smartseason.inventory.web.dto.ReservationResponse;
import com.smartseason.inventory.web.dto.ReservationUpdateRequest;
import com.smartseason.inventory.platform.ListFilter;
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
public class ReservationService {

    private static final String RESOURCE = "Reservation";
    private static final String ENTITY = "reservations";

    private static final Map<String, Class<?>> FILTERABLE = Map.ofEntries(
            Map.entry("stockItemId", UUID.class),
            Map.entry("orderId", UUID.class),
            Map.entry("status", Reservation.Status.class));

    private static final List<String> SEARCHABLE = List.of();

    private final ReservationRepository repository;
    private final EventPublisher events;
    private final CountCache counts;
    private final ReferenceChecker references;

    public ReservationService(ReservationRepository repository, EventPublisher events, CountCache counts,
            ReferenceChecker references) {
        this.repository = repository;
        this.events = events;
        this.counts = counts;
        this.references = references;
    }

    public PageResponse<ReservationResponse> list(Pageable pageable, Map<String, String> params) {
        if (ListFilter.isEmpty(params)) {
            return list(pageable);
        }
        UUID tenantId = TenantContext.requireTenantId();
        var spec = ListFilter.<Reservation>of(tenantId, params, FILTERABLE, SEARCHABLE);
        return PageResponse.from(repository.findAll(spec, pageable).map(ReservationResponse::from));
    }

    public PageResponse<ReservationResponse> list(Pageable pageable) {
        UUID tenantId = TenantContext.requireTenantId();

        return PageResponse.of(
                repository.findAllByTenantId(tenantId, pageable).map(ReservationResponse::from),
                counts.total(ENTITY, tenantId, () -> repository.countByTenantId(tenantId)));
    }

    public CursorPage<ReservationResponse> listByCursor(String cursor, int size) {
        UUID tenantId = TenantContext.requireTenantId();

        Pageable pageable = PageRequest.of(0, Math.max(1, Math.min(size, 200)));

        Cursor from = Cursor.decode(cursor);
        Slice<Reservation> slice = (from == null)
                ? repository.findAllByTenantIdOrderByCreatedAtDescIdDesc(tenantId, pageable)
                : repository.findAfterCursor(tenantId, from.createdAt(), from.id(), pageable);

        return CursorPage.of(
                slice,
                slice.getContent().stream().map(ReservationResponse::from).toList(),
                e -> new Cursor(e.getCreatedAt(), e.getId()));
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

        references.require("StockItem", "stockItemId", request.stockItemId());
        entity.setStockItemId(request.stockItemId());
        entity.setOrderId(request.orderId());
        entity.setQuantity(request.quantity());
        entity.setReservedAt(request.reservedAt());
        entity.setExpiresAt(request.expiresAt());
        entity.setReleasedAt(request.releasedAt());
        entity.setStatus(request.status());

        Reservation saved = repository.save(entity);
        counts.invalidate(ENTITY, saved.getTenantId());
        events.publish("market", "ReservationCreated", saved.getId(), ReservationResponse.from(saved));
        return ReservationResponse.from(saved);
    }

    @Transactional
    public ReservationResponse update(UUID id, ReservationUpdateRequest request) {
        Reservation entity = require(id);
        references.require("StockItem", "stockItemId", request.stockItemId());
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
        counts.invalidate(ENTITY, entity.getTenantId());
        events.publish("market", "ReservationDeleted", id, null);
    }

    private Reservation require(UUID id) {
        return repository.findByIdAndTenantId(id, TenantContext.requireTenantId())
                .orElseThrow(() -> new ResourceNotFoundException(RESOURCE, id));
    }
}
