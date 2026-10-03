package com.smartseason.identity.service;

import com.smartseason.identity.domain.User;
import com.smartseason.identity.platform.CountCache;
import com.smartseason.identity.platform.CountCache;
import com.smartseason.identity.platform.EventPublisher;
import com.smartseason.identity.platform.ReferenceChecker;
import com.smartseason.identity.platform.Cursor;
import com.smartseason.identity.platform.CursorPage;
import com.smartseason.identity.platform.PageResponse;
import com.smartseason.identity.platform.ResourceNotFoundException;
import com.smartseason.identity.platform.TenantContext;
import com.smartseason.identity.repo.UserRepository;
import com.smartseason.identity.web.dto.UserCreateRequest;
import com.smartseason.identity.web.dto.UserResponse;
import com.smartseason.identity.web.dto.UserUpdateRequest;
import com.smartseason.identity.platform.ListFilter;
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
public class UserService {

    private static final String RESOURCE = "User";
    private static final String ENTITY = "users";

    private static final Map<String, Class<?>> FILTERABLE = Map.ofEntries(
            Map.entry("email", String.class),
            Map.entry("phone", String.class),
            Map.entry("fullName", String.class),
            Map.entry("organisationId", UUID.class),
            Map.entry("roles", String.class),
            Map.entry("status", User.Status.class),
            Map.entry("mfaEnabled", Boolean.class),
            Map.entry("locale", String.class));

    private static final List<String> SEARCHABLE = List.of("email", "phone", "fullName", "roles", "locale");

    private final UserRepository repository;
    private final EventPublisher events;
    private final CountCache counts;
    private final ReferenceChecker references;

    public UserService(UserRepository repository, EventPublisher events, CountCache counts,
            ReferenceChecker references) {
        this.repository = repository;
        this.events = events;
        this.counts = counts;
        this.references = references;
    }

    public PageResponse<UserResponse> list(Pageable pageable, Map<String, String> params) {
        if (ListFilter.isEmpty(params)) {
            return list(pageable);
        }
        UUID tenantId = TenantContext.requireTenantId();
        var spec = ListFilter.<User>of(tenantId, params, FILTERABLE, SEARCHABLE);
        return PageResponse.from(repository.findAll(spec, pageable).map(UserResponse::from));
    }

    public PageResponse<UserResponse> list(Pageable pageable) {
        UUID tenantId = TenantContext.requireTenantId();

        return PageResponse.of(
                repository.findAllByTenantId(tenantId, pageable).map(UserResponse::from),
                counts.total(ENTITY, tenantId, () -> repository.countByTenantId(tenantId)));
    }

    public CursorPage<UserResponse> listByCursor(String cursor, int size) {
        UUID tenantId = TenantContext.requireTenantId();

        Pageable pageable = PageRequest.of(0, Math.max(1, Math.min(size, 200)));

        Cursor from = Cursor.decode(cursor);
        Slice<User> slice = (from == null)
                ? repository.findAllByTenantIdOrderByCreatedAtDescIdDesc(tenantId, pageable)
                : repository.findAfterCursor(tenantId, from.createdAt(), from.id(), pageable);

        return CursorPage.of(
                slice,
                slice.getContent().stream().map(UserResponse::from).toList(),
                e -> new Cursor(e.getCreatedAt(), e.getId()));
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

        references.require("Organisation", "organisationId", request.organisationId());
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
        counts.invalidate(ENTITY, saved.getTenantId());
        events.publish("identity", "UserCreated", saved.getId(), UserResponse.from(saved));
        return UserResponse.from(saved);
    }

    @Transactional
    public UserResponse update(UUID id, UserUpdateRequest request) {
        User entity = require(id);
        references.require("Organisation", "organisationId", request.organisationId());
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
        counts.invalidate(ENTITY, entity.getTenantId());
        events.publish("identity", "UserDeleted", id, null);
    }

    private User require(UUID id) {
        return repository.findByIdAndTenantId(id, TenantContext.requireTenantId())
                .orElseThrow(() -> new ResourceNotFoundException(RESOURCE, id));
    }
}
