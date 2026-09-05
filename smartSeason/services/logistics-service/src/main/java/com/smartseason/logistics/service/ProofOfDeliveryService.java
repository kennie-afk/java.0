package com.smartseason.logistics.service;

import com.smartseason.logistics.domain.ProofOfDelivery;
import com.smartseason.logistics.platform.EventPublisher;
import com.smartseason.logistics.platform.PageResponse;
import com.smartseason.logistics.platform.ResourceNotFoundException;
import com.smartseason.logistics.platform.TenantContext;
import com.smartseason.logistics.repo.ProofOfDeliveryRepository;
import com.smartseason.logistics.web.dto.ProofOfDeliveryCreateRequest;
import com.smartseason.logistics.web.dto.ProofOfDeliveryResponse;
import com.smartseason.logistics.web.dto.ProofOfDeliveryUpdateRequest;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class ProofOfDeliveryService {

    private static final String RESOURCE = "ProofOfDelivery";

    private final ProofOfDeliveryRepository repository;
    private final EventPublisher events;

    public ProofOfDeliveryService(ProofOfDeliveryRepository repository, EventPublisher events) {
        this.repository = repository;
        this.events = events;
    }

    public PageResponse<ProofOfDeliveryResponse> list(Pageable pageable) {
        return PageResponse.from(
                repository.findAllByTenantId(TenantContext.requireTenantId(), pageable)
                        .map(ProofOfDeliveryResponse::from));
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
        events.publish("market", "ProofOfDeliveryCreated", saved.getId(), ProofOfDeliveryResponse.from(saved));
        return ProofOfDeliveryResponse.from(saved);
    }

    @Transactional
    public ProofOfDeliveryResponse update(UUID id, ProofOfDeliveryUpdateRequest request) {
        ProofOfDelivery entity = require(id);
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
        events.publish("market", "ProofOfDeliveryDeleted", id, null);
    }

    private ProofOfDelivery require(UUID id) {
        return repository.findByIdAndTenantId(id, TenantContext.requireTenantId())
                .orElseThrow(() -> new ResourceNotFoundException(RESOURCE, id));
    }
}
