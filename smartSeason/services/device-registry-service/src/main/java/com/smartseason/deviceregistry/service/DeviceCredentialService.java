package com.smartseason.deviceregistry.service;

import com.smartseason.deviceregistry.domain.DeviceCredential;
import com.smartseason.deviceregistry.platform.EventPublisher;
import com.smartseason.deviceregistry.platform.PageResponse;
import com.smartseason.deviceregistry.platform.ResourceNotFoundException;
import com.smartseason.deviceregistry.platform.TenantContext;
import com.smartseason.deviceregistry.repo.DeviceCredentialRepository;
import com.smartseason.deviceregistry.web.dto.DeviceCredentialCreateRequest;
import com.smartseason.deviceregistry.web.dto.DeviceCredentialResponse;
import com.smartseason.deviceregistry.web.dto.DeviceCredentialUpdateRequest;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class DeviceCredentialService {

    private static final String RESOURCE = "DeviceCredential";

    private final DeviceCredentialRepository repository;
    private final EventPublisher events;

    public DeviceCredentialService(DeviceCredentialRepository repository, EventPublisher events) {
        this.repository = repository;
        this.events = events;
    }

    public PageResponse<DeviceCredentialResponse> list(Pageable pageable) {
        return PageResponse.from(
                repository.findAllByTenantId(TenantContext.requireTenantId(), pageable)
                        .map(DeviceCredentialResponse::from));
    }

    public DeviceCredentialResponse get(UUID id) {
        return DeviceCredentialResponse.from(require(id));
    }

    public long count() {
        return repository.countByTenantId(TenantContext.requireTenantId());
    }

    @Transactional
    public DeviceCredentialResponse create(DeviceCredentialCreateRequest request) {
        DeviceCredential entity = new DeviceCredential();
        entity.setTenantId(TenantContext.requireTenantId());
        entity.setDeviceId(request.deviceId());
        entity.setCredentialType(request.credentialType());
        entity.setPublicKey(request.publicKey());
        entity.setFingerprint(request.fingerprint());
        entity.setIssuedAt(request.issuedAt());
        entity.setExpiresAt(request.expiresAt());
        entity.setRevokedAt(request.revokedAt());

        DeviceCredential saved = repository.save(entity);
        events.publish("iot", "DeviceCredentialCreated", saved.getId(), DeviceCredentialResponse.from(saved));
        return DeviceCredentialResponse.from(saved);
    }

    @Transactional
    public DeviceCredentialResponse update(UUID id, DeviceCredentialUpdateRequest request) {
        DeviceCredential entity = require(id);
        if (request.deviceId() != null) {
            entity.setDeviceId(request.deviceId());
        }
        if (request.credentialType() != null) {
            entity.setCredentialType(request.credentialType());
        }
        if (request.publicKey() != null) {
            entity.setPublicKey(request.publicKey());
        }
        if (request.fingerprint() != null) {
            entity.setFingerprint(request.fingerprint());
        }
        if (request.issuedAt() != null) {
            entity.setIssuedAt(request.issuedAt());
        }
        if (request.expiresAt() != null) {
            entity.setExpiresAt(request.expiresAt());
        }
        if (request.revokedAt() != null) {
            entity.setRevokedAt(request.revokedAt());
        }

        DeviceCredential saved = repository.save(entity);
        events.publish("iot", "DeviceCredentialUpdated", saved.getId(), DeviceCredentialResponse.from(saved));
        return DeviceCredentialResponse.from(saved);
    }

    @Transactional
    public void delete(UUID id) {
        DeviceCredential entity = require(id);
        repository.delete(entity);
        events.publish("iot", "DeviceCredentialDeleted", id, null);
    }

    private DeviceCredential require(UUID id) {
        return repository.findByIdAndTenantId(id, TenantContext.requireTenantId())
                .orElseThrow(() -> new ResourceNotFoundException(RESOURCE, id));
    }
}
