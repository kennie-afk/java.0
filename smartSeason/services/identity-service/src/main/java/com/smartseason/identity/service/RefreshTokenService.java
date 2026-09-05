package com.smartseason.identity.service;

import com.smartseason.identity.domain.RefreshToken;
import com.smartseason.identity.platform.EventPublisher;
import com.smartseason.identity.platform.PageResponse;
import com.smartseason.identity.platform.ResourceNotFoundException;
import com.smartseason.identity.platform.TenantContext;
import com.smartseason.identity.repo.RefreshTokenRepository;
import com.smartseason.identity.web.dto.RefreshTokenCreateRequest;
import com.smartseason.identity.web.dto.RefreshTokenResponse;
import com.smartseason.identity.web.dto.RefreshTokenUpdateRequest;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class RefreshTokenService {

    private static final String RESOURCE = "RefreshToken";

    private final RefreshTokenRepository repository;
    private final EventPublisher events;

    public RefreshTokenService(RefreshTokenRepository repository, EventPublisher events) {
        this.repository = repository;
        this.events = events;
    }

    public PageResponse<RefreshTokenResponse> list(Pageable pageable) {
        return PageResponse.from(
                repository.findAllByTenantId(TenantContext.requireTenantId(), pageable)
                        .map(RefreshTokenResponse::from));
    }

    public RefreshTokenResponse get(UUID id) {
        return RefreshTokenResponse.from(require(id));
    }

    public long count() {
        return repository.countByTenantId(TenantContext.requireTenantId());
    }

    @Transactional
    public RefreshTokenResponse create(RefreshTokenCreateRequest request) {
        RefreshToken entity = new RefreshToken();
        entity.setTenantId(TenantContext.requireTenantId());
        entity.setUserId(request.userId());
        entity.setTokenHash(request.tokenHash());
        entity.setExpiresAt(request.expiresAt());
        entity.setRevokedAt(request.revokedAt());
        entity.setUserAgent(request.userAgent());
        entity.setIp(request.ip());

        RefreshToken saved = repository.save(entity);
        events.publish("identity", "RefreshTokenCreated", saved.getId(), RefreshTokenResponse.from(saved));
        return RefreshTokenResponse.from(saved);
    }

    @Transactional
    public RefreshTokenResponse update(UUID id, RefreshTokenUpdateRequest request) {
        RefreshToken entity = require(id);
        if (request.userId() != null) {
            entity.setUserId(request.userId());
        }
        if (request.tokenHash() != null) {
            entity.setTokenHash(request.tokenHash());
        }
        if (request.expiresAt() != null) {
            entity.setExpiresAt(request.expiresAt());
        }
        if (request.revokedAt() != null) {
            entity.setRevokedAt(request.revokedAt());
        }
        if (request.userAgent() != null) {
            entity.setUserAgent(request.userAgent());
        }
        if (request.ip() != null) {
            entity.setIp(request.ip());
        }

        RefreshToken saved = repository.save(entity);
        events.publish("identity", "RefreshTokenUpdated", saved.getId(), RefreshTokenResponse.from(saved));
        return RefreshTokenResponse.from(saved);
    }

    @Transactional
    public void delete(UUID id) {
        RefreshToken entity = require(id);
        repository.delete(entity);
        events.publish("identity", "RefreshTokenDeleted", id, null);
    }

    private RefreshToken require(UUID id) {
        return repository.findByIdAndTenantId(id, TenantContext.requireTenantId())
                .orElseThrow(() -> new ResourceNotFoundException(RESOURCE, id));
    }
}
