package com.smartseason.logistics.service;

import com.smartseason.logistics.domain.TransportJob;
import com.smartseason.logistics.platform.EventPublisher;
import com.smartseason.logistics.platform.PageResponse;
import com.smartseason.logistics.platform.ResourceNotFoundException;
import com.smartseason.logistics.platform.TenantContext;
import com.smartseason.logistics.repo.TransportJobRepository;
import com.smartseason.logistics.web.dto.TransportJobCreateRequest;
import com.smartseason.logistics.web.dto.TransportJobResponse;
import com.smartseason.logistics.web.dto.TransportJobUpdateRequest;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class TransportJobService {

    private static final String RESOURCE = "TransportJob";

    private final TransportJobRepository repository;
    private final EventPublisher events;

    public TransportJobService(TransportJobRepository repository, EventPublisher events) {
        this.repository = repository;
        this.events = events;
    }

    public PageResponse<TransportJobResponse> list(Pageable pageable) {
        return PageResponse.from(
                repository.findAllByTenantId(TenantContext.requireTenantId(), pageable)
                        .map(TransportJobResponse::from));
    }

    public TransportJobResponse get(UUID id) {
        return TransportJobResponse.from(require(id));
    }

    public long count() {
        return repository.countByTenantId(TenantContext.requireTenantId());
    }

    @Transactional
    public TransportJobResponse create(TransportJobCreateRequest request) {
        TransportJob entity = new TransportJob();
        entity.setTenantId(TenantContext.requireTenantId());
        entity.setJobNumber(request.jobNumber());
        entity.setOrderId(request.orderId());
        entity.setBatchId(request.batchId());
        entity.setVehicleId(request.vehicleId());
        entity.setDriverId(request.driverId());
        entity.setPickupCounty(request.pickupCounty());
        entity.setPickupLat(request.pickupLat());
        entity.setPickupLng(request.pickupLng());
        entity.setPickupAt(request.pickupAt());
        entity.setDropoffCounty(request.dropoffCounty());
        entity.setDropoffLat(request.dropoffLat());
        entity.setDropoffLng(request.dropoffLng());
        entity.setDropoffAt(request.dropoffAt());
        entity.setDistanceKm(request.distanceKm());
        entity.setWeightKg(request.weightKg());
        entity.setFreightCost(request.freightCost());
        entity.setCurrency(request.currency());
        entity.setRequiresColdChain(request.requiresColdChain());
        entity.setStatus(request.status());

        TransportJob saved = repository.save(entity);
        events.publish("market", "TransportJobCreated", saved.getId(), TransportJobResponse.from(saved));
        return TransportJobResponse.from(saved);
    }

    @Transactional
    public TransportJobResponse update(UUID id, TransportJobUpdateRequest request) {
        TransportJob entity = require(id);
        if (request.jobNumber() != null) {
            entity.setJobNumber(request.jobNumber());
        }
        if (request.orderId() != null) {
            entity.setOrderId(request.orderId());
        }
        if (request.batchId() != null) {
            entity.setBatchId(request.batchId());
        }
        if (request.vehicleId() != null) {
            entity.setVehicleId(request.vehicleId());
        }
        if (request.driverId() != null) {
            entity.setDriverId(request.driverId());
        }
        if (request.pickupCounty() != null) {
            entity.setPickupCounty(request.pickupCounty());
        }
        if (request.pickupLat() != null) {
            entity.setPickupLat(request.pickupLat());
        }
        if (request.pickupLng() != null) {
            entity.setPickupLng(request.pickupLng());
        }
        if (request.pickupAt() != null) {
            entity.setPickupAt(request.pickupAt());
        }
        if (request.dropoffCounty() != null) {
            entity.setDropoffCounty(request.dropoffCounty());
        }
        if (request.dropoffLat() != null) {
            entity.setDropoffLat(request.dropoffLat());
        }
        if (request.dropoffLng() != null) {
            entity.setDropoffLng(request.dropoffLng());
        }
        if (request.dropoffAt() != null) {
            entity.setDropoffAt(request.dropoffAt());
        }
        if (request.distanceKm() != null) {
            entity.setDistanceKm(request.distanceKm());
        }
        if (request.weightKg() != null) {
            entity.setWeightKg(request.weightKg());
        }
        if (request.freightCost() != null) {
            entity.setFreightCost(request.freightCost());
        }
        if (request.currency() != null) {
            entity.setCurrency(request.currency());
        }
        if (request.requiresColdChain() != null) {
            entity.setRequiresColdChain(request.requiresColdChain());
        }
        if (request.status() != null) {
            entity.setStatus(request.status());
        }

        TransportJob saved = repository.save(entity);
        events.publish("market", "TransportJobUpdated", saved.getId(), TransportJobResponse.from(saved));
        return TransportJobResponse.from(saved);
    }

    @Transactional
    public void delete(UUID id) {
        TransportJob entity = require(id);
        repository.delete(entity);
        events.publish("market", "TransportJobDeleted", id, null);
    }

    private TransportJob require(UUID id) {
        return repository.findByIdAndTenantId(id, TenantContext.requireTenantId())
                .orElseThrow(() -> new ResourceNotFoundException(RESOURCE, id));
    }
}
