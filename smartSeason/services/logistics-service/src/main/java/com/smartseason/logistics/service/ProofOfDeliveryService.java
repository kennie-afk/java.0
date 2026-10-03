package com.smartseason.logistics.service;

import com.smartseason.logistics.domain.ProofOfDelivery;
import com.smartseason.logistics.platform.CountCache;
import com.smartseason.logistics.platform.CountCache;
import com.smartseason.logistics.platform.EventPublisher;
import com.smartseason.logistics.platform.ReferenceChecker;
import com.smartseason.logistics.platform.Cursor;
import com.smartseason.logistics.platform.CursorPage;
import com.smartseason.logistics.platform.PageResponse;
import com.smartseason.logistics.platform.ResourceNotFoundException;
import com.smartseason.logistics.platform.TenantContext;
import com.smartseason.logistics.repo.ProofOfDeliveryRepository;
import com.smartseason.logistics.web.dto.ProofOfDeliveryCreateRequest;
import com.smartseason.logistics.web.dto.ProofOfDeliveryResponse;
import com.smartseason.logistics.web.dto.ProofOfDeliveryUpdateRequest;
import com.smartseason.logistics.platform.ListFilter;
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
public class ProofOfDeliveryService {

    private static final String RESOURCE = "ProofOfDelivery";
    private static final String ENTITY = "proofs_of_delivery";

    private static final Map<String, Class<?>> FILTERABLE = Map.ofEntries(
            Map.entry("transportJobId", UUID.class),
            Map.entry("receivedBy", String.class),
            Map.entry("signatureUrl", String.class),
            Map.entry("photoUrl", String.class),
            Map.entry("disputed", Boolean.class));

    private static final List<String> SEARCHABLE = List.of("receivedBy", "signatureUrl", "photoUrl");

    private final ProofOfDeliveryRepository repository;
    private final EventPublisher events;
    private final CountCache counts;
    private final ReferenceChecker references;

    public ProofOfDeliveryService(ProofOfDeliveryRepository repository, EventPublisher events, CountCache counts,
            ReferenceChecker references) {
        this.repository = repository;
        this.events = events;
        this.counts = counts;
        this.references = references;
    }

    public PageResponse<ProofOfDeliveryResponse> list(Pageable pageable, Map<String, String> params) {
        if (ListFilter.isEmpty(params)) {
            return list(pageable);
        }
        UUID tenantId = TenantContext.requireTenantId();
        var spec = ListFilter.<ProofOfDelivery>of(tenantId, params, FILTERABLE, SEARCHABLE);
        return PageResponse.from(repository.findAll(spec, pageable).map(ProofOfDeliveryResponse::from));
    }

    public PageResponse<ProofOfDeliveryResponse> list(Pageable pageable) {
        UUID tenantId = TenantContext.requireTenantId();

        return PageResponse.of(
                repository.findAllByTenantId(tenantId, pageable).map(ProofOfDeliveryResponse::from),
                counts.total(ENTITY, tenantId, () -> repository.countByTenantId(tenantId)));
    }

    public CursorPage<ProofOfDeliveryResponse> listByCursor(String cursor, int size) {
        UUID tenantId = TenantContext.requireTenantId();

        Pageable pageable = PageRequest.of(0, Math.max(1, Math.min(size, 200)));

        Cursor from = Cursor.decode(cursor);
        Slice<ProofOfDelivery> slice = (from == null)
                ? repository.findAllByTenantIdOrderByCreatedAtDescIdDesc(tenantId, pageable)
                : repository.findAfterCursor(tenantId, from.createdAt(), from.id(), pageable);

        return CursorPage.of(
                slice,
                slice.getContent().stream().map(ProofOfDeliveryResponse::from).toList(),
                e -> new Cursor(e.getCreatedAt(), e.getId()));
    }

    public ProofOfDeliveryResponse get(UUID id) {
        return ProofOfDeliveryResponse.from(require(id));
    }

    public long count() {
        return repository.countByTenantId(TenantContext.requireTenantId());
    }

    @Transactional
    public ProofOfDeliveryResponse create(ProofOfDeliveryCreateRequest request) {
        ProofOfDelivery entity = new ProofOfDelivery();
        entity.setTenantId(TenantContext.requireTenantId());

        references.require("TransportJob", "transportJobId", request.transportJobId());
        entity.setTransportJobId(request.transportJobId());
        entity.setReceivedBy(request.receivedBy());
        entity.setReceivedAt(request.receivedAt());
        entity.setSignatureUrl(request.signatureUrl());
        entity.setPhotoUrl(request.photoUrl());
        entity.setLatitude(request.latitude());
        entity.setLongitude(request.longitude());
        entity.setDeliveredWeightKg(request.deliveredWeightKg());
        entity.setVarianceKg(request.varianceKg());
        entity.setNotes(request.notes());
        entity.setDisputed(request.disputed());

        ProofOfDelivery saved = repository.save(entity);
        counts.invalidate(ENTITY, saved.getTenantId());
        events.publish("market", "ProofOfDeliveryCreated", saved.getId(), ProofOfDeliveryResponse.from(saved));
        return ProofOfDeliveryResponse.from(saved);
    }

    @Transactional
    public ProofOfDeliveryResponse update(UUID id, ProofOfDeliveryUpdateRequest request) {
        ProofOfDelivery entity = require(id);
        references.require("TransportJob", "transportJobId", request.transportJobId());
        if (request.transportJobId() != null) {
            entity.setTransportJobId(request.transportJobId());
        }
        if (request.receivedBy() != null) {
            entity.setReceivedBy(request.receivedBy());
        }
        if (request.receivedAt() != null) {
            entity.setReceivedAt(request.receivedAt());
        }
        if (request.signatureUrl() != null) {
            entity.setSignatureUrl(request.signatureUrl());
        }
        if (request.photoUrl() != null) {
            entity.setPhotoUrl(request.photoUrl());
        }
        if (request.latitude() != null) {
            entity.setLatitude(request.latitude());
        }
        if (request.longitude() != null) {
            entity.setLongitude(request.longitude());
        }
        if (request.deliveredWeightKg() != null) {
            entity.setDeliveredWeightKg(request.deliveredWeightKg());
        }
        if (request.varianceKg() != null) {
            entity.setVarianceKg(request.varianceKg());
        }
        if (request.notes() != null) {
            entity.setNotes(request.notes());
        }
        if (request.disputed() != null) {
            entity.setDisputed(request.disputed());
        }

        ProofOfDelivery saved = repository.save(entity);
        events.publish("market", "ProofOfDeliveryUpdated", saved.getId(), ProofOfDeliveryResponse.from(saved));
        return ProofOfDeliveryResponse.from(saved);
    }

    @Transactional
    public void delete(UUID id) {
        ProofOfDelivery entity = require(id);
        repository.delete(entity);
        counts.invalidate(ENTITY, entity.getTenantId());
        events.publish("market", "ProofOfDeliveryDeleted", id, null);
    }

    private ProofOfDelivery require(UUID id) {
        return repository.findByIdAndTenantId(id, TenantContext.requireTenantId())
                .orElseThrow(() -> new ResourceNotFoundException(RESOURCE, id));
    }
}
