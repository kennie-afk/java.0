package com.smartseason.deviceregistry.service;

import com.smartseason.deviceregistry.domain.FirmwareRelease;
import com.smartseason.deviceregistry.platform.EventPublisher;
import com.smartseason.deviceregistry.platform.PageResponse;
import com.smartseason.deviceregistry.platform.ResourceNotFoundException;
import com.smartseason.deviceregistry.platform.TenantContext;
import com.smartseason.deviceregistry.repo.FirmwareReleaseRepository;
import com.smartseason.deviceregistry.web.dto.FirmwareReleaseCreateRequest;
import com.smartseason.deviceregistry.web.dto.FirmwareReleaseResponse;
import com.smartseason.deviceregistry.web.dto.FirmwareReleaseUpdateRequest;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class FirmwareReleaseService {

    private static final String RESOURCE = "FirmwareRelease";

    private final FirmwareReleaseRepository repository;
    private final EventPublisher events;

    public FirmwareReleaseService(FirmwareReleaseRepository repository, EventPublisher events) {
        this.repository = repository;
        this.events = events;
    }

    public PageResponse<FirmwareReleaseResponse> list(Pageable pageable) {
        return PageResponse.from(
                repository.findAllByTenantId(TenantContext.requireTenantId(), pageable)
                        .map(FirmwareReleaseResponse::from));
    }

    public FirmwareReleaseResponse get(UUID id) {
        return FirmwareReleaseResponse.from(require(id));
    }

    public long count() {
        return repository.countByTenantId(TenantContext.requireTenantId());
    }

    @Transactional
    public FirmwareReleaseResponse create(FirmwareReleaseCreateRequest request) {
        FirmwareRelease entity = new FirmwareRelease();
        entity.setTenantId(TenantContext.requireTenantId());
        entity.setDeviceType(request.deviceType());
        entity.setReleaseVersion(request.releaseVersion());
        entity.setArtifactUrl(request.artifactUrl());
        entity.setChecksum(request.checksum());
        entity.setReleaseNotes(request.releaseNotes());
        entity.setMandatory(request.mandatory());
        entity.setPublishedAt(request.publishedAt());

        FirmwareRelease saved = repository.save(entity);
        events.publish("iot", "FirmwareReleaseCreated", saved.getId(), FirmwareReleaseResponse.from(saved));
        return FirmwareReleaseResponse.from(saved);
    }

    @Transactional
    public FirmwareReleaseResponse update(UUID id, FirmwareReleaseUpdateRequest request) {
        FirmwareRelease entity = require(id);
        if (request.deviceType() != null) {
            entity.setDeviceType(request.deviceType());
        }
        if (request.releaseVersion() != null) {
            entity.setReleaseVersion(request.releaseVersion());
        }
        if (request.artifactUrl() != null) {
            entity.setArtifactUrl(request.artifactUrl());
        }
        if (request.checksum() != null) {
            entity.setChecksum(request.checksum());
        }
        if (request.releaseNotes() != null) {
            entity.setReleaseNotes(request.releaseNotes());
        }
        if (request.mandatory() != null) {
            entity.setMandatory(request.mandatory());
        }
        if (request.publishedAt() != null) {
            entity.setPublishedAt(request.publishedAt());
        }

        FirmwareRelease saved = repository.save(entity);
        events.publish("iot", "FirmwareReleaseUpdated", saved.getId(), FirmwareReleaseResponse.from(saved));
        return FirmwareReleaseResponse.from(saved);
    }

    @Transactional
    public void delete(UUID id) {
        FirmwareRelease entity = require(id);
        repository.delete(entity);
        events.publish("iot", "FirmwareReleaseDeleted", id, null);
    }

    private FirmwareRelease require(UUID id) {
        return repository.findByIdAndTenantId(id, TenantContext.requireTenantId())
                .orElseThrow(() -> new ResourceNotFoundException(RESOURCE, id));
    }
}
