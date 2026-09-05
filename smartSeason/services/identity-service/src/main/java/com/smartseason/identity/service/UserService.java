package com.smartseason.identity.service;

import com.smartseason.identity.domain.User;
import com.smartseason.identity.platform.EventPublisher;
import com.smartseason.identity.platform.PageResponse;
import com.smartseason.identity.platform.ResourceNotFoundException;
import com.smartseason.identity.platform.TenantContext;
import com.smartseason.identity.repo.UserRepository;
import com.smartseason.identity.web.dto.UserCreateRequest;
import com.smartseason.identity.web.dto.UserResponse;
import com.smartseason.identity.web.dto.UserUpdateRequest;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class UserService {

    private static final String RESOURCE = "User";

    private final UserRepository repository;
    private final EventPublisher events;

    public UserService(UserRepository repository, EventPublisher events) {
        this.repository = repository;
        this.events = events;
    }

    public PageResponse<UserResponse> list(Pageable pageable) {
        return PageResponse.from(
                repository.findAllByTenantId(TenantContext.requireTenantId(), pageable)
                        .map(UserResponse::from));
    }

    public UserResponse get(UUID id) {
        return UserResponse.from(require(id));
    }

    public long count() {
        return repository.countByTenantId(TenantContext.requireTenantId());
    }

    @Transactional
    public UserResponse create(UserCreateRequest request) {
        User entity = new User();
        entity.setTenantId(TenantContext.requireTenantId());
        entity.setEmail(request.email());
        entity.setPhone(request.phone());
        entity.setFullName(request.fullName());
        entity.setPasswordHash(request.passwordHash());
        entity.setOrganisationId(request.organisationId());
        entity.setRoles(request.roles());
        entity.setStatus(request.status());
        entity.setMfaEnabled(request.mfaEnabled());
        entity.setLastLoginAt(request.lastLoginAt());
        entity.setFailedAttempts(request.failedAttempts());
        entity.setLocale(request.locale());

        User saved = repository.save(entity);
        events.publish("identity", "UserCreated", saved.getId(), UserResponse.from(saved));
        return UserResponse.from(saved);
    }

    @Transactional
    public UserResponse update(UUID id, UserUpdateRequest request) {
        User entity = require(id);
        if (request.email() != null) {
            entity.setEmail(request.email());
        }
        if (request.phone() != null) {
            entity.setPhone(request.phone());
        }
        if (request.fullName() != null) {
            entity.setFullName(request.fullName());
        }
        if (request.passwordHash() != null) {
            entity.setPasswordHash(request.passwordHash());
        }
        if (request.organisationId() != null) {
            entity.setOrganisationId(request.organisationId());
        }
        if (request.roles() != null) {
            entity.setRoles(request.roles());
        }
        if (request.status() != null) {
            entity.setStatus(request.status());
        }
        if (request.mfaEnabled() != null) {
            entity.setMfaEnabled(request.mfaEnabled());
        }
        if (request.lastLoginAt() != null) {
            entity.setLastLoginAt(request.lastLoginAt());
        }
        if (request.failedAttempts() != null) {
            entity.setFailedAttempts(request.failedAttempts());
        }
        if (request.locale() != null) {
            entity.setLocale(request.locale());
        }

        User saved = repository.save(entity);
        events.publish("identity", "UserUpdated", saved.getId(), UserResponse.from(saved));
        return UserResponse.from(saved);
    }

    @Transactional
    public void delete(UUID id) {
        User entity = require(id);
        repository.delete(entity);
        events.publish("identity", "UserDeleted", id, null);
    }

    private User require(UUID id) {
        return repository.findByIdAndTenantId(id, TenantContext.requireTenantId())
                .orElseThrow(() -> new ResourceNotFoundException(RESOURCE, id));
    }
}
