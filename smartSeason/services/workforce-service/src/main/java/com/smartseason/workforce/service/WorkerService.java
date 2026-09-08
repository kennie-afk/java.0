package com.smartseason.workforce.service;

import com.smartseason.workforce.domain.Worker;
import com.smartseason.workforce.platform.CountCache;
import com.smartseason.workforce.platform.CountCache;
import com.smartseason.workforce.platform.EventPublisher;
import com.smartseason.workforce.platform.PageResponse;
import com.smartseason.workforce.platform.ResourceNotFoundException;
import com.smartseason.workforce.platform.TenantContext;
import com.smartseason.workforce.repo.WorkerRepository;
import com.smartseason.workforce.web.dto.WorkerCreateRequest;
import com.smartseason.workforce.web.dto.WorkerResponse;
import com.smartseason.workforce.web.dto.WorkerUpdateRequest;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class WorkerService {

    private static final String RESOURCE = "Worker";
    private static final String ENTITY = "workers";

    private final WorkerRepository repository;
    private final EventPublisher events;
    private final CountCache counts;

    public WorkerService(WorkerRepository repository, EventPublisher events, CountCache counts) {
        this.repository = repository;
        this.events = events;
        this.counts = counts;
    }

    public PageResponse<WorkerResponse> list(Pageable pageable) {
        UUID tenantId = TenantContext.requireTenantId();

        return PageResponse.of(
                repository.findAllByTenantId(tenantId, pageable).map(WorkerResponse::from),
                counts.total(ENTITY, tenantId, () -> repository.countByTenantId(tenantId)));
    }

    public WorkerResponse get(UUID id) {
        return WorkerResponse.from(require(id));
    }

    public long count() {
        return repository.countByTenantId(TenantContext.requireTenantId());
    }

    @Transactional
    public WorkerResponse create(WorkerCreateRequest request) {
        Worker entity = new Worker();
        entity.setTenantId(TenantContext.requireTenantId());
        entity.setUserId(request.userId());
        entity.setNationalId(request.nationalId());
        entity.setFullName(request.fullName());
        entity.setPhone(request.phone());
        entity.setGender(request.gender());
        entity.setDateOfBirth(request.dateOfBirth());
        entity.setFarmId(request.farmId());
        entity.setPayoutPhone(request.payoutPhone());
        entity.setPayoutAccount(request.payoutAccount());
        entity.setBiometricRef(request.biometricRef());
        entity.setStatus(request.status());
        entity.setRiskScore(request.riskScore());
        entity.setOnboardedAt(request.onboardedAt());
        entity.setPhotoUrl(request.photoUrl());

        Worker saved = repository.save(entity);
        counts.invalidate(ENTITY, saved.getTenantId());
        events.publish("workforce", "WorkerCreated", saved.getId(), WorkerResponse.from(saved));
        return WorkerResponse.from(saved);
    }

    @Transactional
    public WorkerResponse update(UUID id, WorkerUpdateRequest request) {
        Worker entity = require(id);
        if (request.userId() != null) {
            entity.setUserId(request.userId());
        }
        if (request.nationalId() != null) {
            entity.setNationalId(request.nationalId());
        }
        if (request.fullName() != null) {
            entity.setFullName(request.fullName());
        }
        if (request.phone() != null) {
            entity.setPhone(request.phone());
        }
        if (request.gender() != null) {
            entity.setGender(request.gender());
        }
        if (request.dateOfBirth() != null) {
            entity.setDateOfBirth(request.dateOfBirth());
        }
        if (request.farmId() != null) {
            entity.setFarmId(request.farmId());
        }
        if (request.payoutPhone() != null) {
            entity.setPayoutPhone(request.payoutPhone());
        }
        if (request.payoutAccount() != null) {
            entity.setPayoutAccount(request.payoutAccount());
        }
        if (request.biometricRef() != null) {
            entity.setBiometricRef(request.biometricRef());
        }
        if (request.status() != null) {
            entity.setStatus(request.status());
        }
        if (request.riskScore() != null) {
            entity.setRiskScore(request.riskScore());
        }
        if (request.onboardedAt() != null) {
            entity.setOnboardedAt(request.onboardedAt());
        }
        if (request.photoUrl() != null) {
            entity.setPhotoUrl(request.photoUrl());
        }

        Worker saved = repository.save(entity);
        events.publish("workforce", "WorkerUpdated", saved.getId(), WorkerResponse.from(saved));
        return WorkerResponse.from(saved);
    }

    @Transactional
    public void delete(UUID id) {
        Worker entity = require(id);
        repository.delete(entity);
        counts.invalidate(ENTITY, entity.getTenantId());
        events.publish("workforce", "WorkerDeleted", id, null);
    }

    private Worker require(UUID id) {
        return repository.findByIdAndTenantId(id, TenantContext.requireTenantId())
                .orElseThrow(() -> new ResourceNotFoundException(RESOURCE, id));
    }
}
