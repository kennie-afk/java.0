package com.smartseason.identity.service;

import com.smartseason.identity.domain.RefreshToken;
import com.smartseason.identity.platform.CountCache;
import com.smartseason.identity.platform.CountCache;
import com.smartseason.identity.platform.EventPublisher;
import com.smartseason.identity.platform.ReferenceChecker;
import com.smartseason.identity.platform.Cursor;
import com.smartseason.identity.platform.CursorPage;
import com.smartseason.identity.platform.PageResponse;
import com.smartseason.identity.platform.ResourceNotFoundException;
import com.smartseason.identity.platform.TenantContext;
import com.smartseason.identity.repo.RefreshTokenRepository;
import com.smartseason.identity.web.dto.RefreshTokenCreateRequest;
import com.smartseason.identity.web.dto.RefreshTokenResponse;
import com.smartseason.identity.web.dto.RefreshTokenUpdateRequest;
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
public class RefreshTokenService {

    private static final String RESOURCE = "RefreshToken";
    private static final String ENTITY = "refresh_tokens";

    private static final Map<String, Class<?>> FILTERABLE = Map.ofEntries(
            Map.entry("userId", UUID.class),
            Map.entry("userAgent", String.class),
            Map.entry("ip", String.class));

    private static final List<String> SEARCHABLE = List.of("userAgent", "ip");

    private final RefreshTokenRepository repository;
    private final EventPublisher events;
    private final CountCache counts;
    private final ReferenceChecker references;

    public RefreshTokenService(RefreshTokenRepository repository, EventPublisher events, CountCache counts,
            ReferenceChecker references) {
        this.repository = repository;
        this.events = events;
        this.counts = counts;
        this.references = references;
    }

    public PageResponse<RefreshTokenResponse> list(Pageable pageable, Map<String, String> params) {
        if (ListFilter.isEmpty(params)) {
            return list(pageable);
        }
        UUID tenantId = TenantContext.requireTenantId();
        var spec = ListFilter.<RefreshToken>of(tenantId, params, FILTERABLE, SEARCHABLE);
        return PageResponse.from(repository.findAll(spec, pageable).map(RefreshTokenResponse::from));
    }

    public PageResponse<RefreshTokenResponse> list(Pageable pageable) {
        UUID tenantId = TenantContext.requireTenantId();

        return PageResponse.of(
                repository.findAllByTenantId(tenantId, pageable).map(RefreshTokenResponse::from),
                counts.total(ENTITY, tenantId, () -> repository.countByTenantId(tenantId)));
    }

    public CursorPage<RefreshTokenResponse> listByCursor(String cursor, int size) {
        UUID tenantId = TenantContext.requireTenantId();

        Pageable pageable = PageRequest.of(0, Math.max(1, Math.min(size, 200)));

        Cursor from = Cursor.decode(cursor);
        Slice<RefreshToken> slice = (from == null)
                ? repository.findAllByTenantIdOrderByCreatedAtDescIdDesc(tenantId, pageable)
                : repository.findAfterCursor(tenantId, from.createdAt(), from.id(), pageable);

        return CursorPage.of(
                slice,
                slice.getContent().stream().map(RefreshTokenResponse::from).toList(),
                e -> new Cursor(e.getCreatedAt(), e.getId()));
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

        references.require("User", "userId", request.userId());
        entity.setUserId(request.userId());
        entity.setTokenHash(request.tokenHash());
        entity.setExpiresAt(request.expiresAt());
        entity.setRevokedAt(request.revokedAt());
        entity.setUserAgent(request.userAgent());
        entity.setIp(request.ip());

        RefreshToken saved = repository.save(entity);
        counts.invalidate(ENTITY, saved.getTenantId());
        events.publish("identity", "RefreshTokenCreated", saved.getId(), RefreshTokenResponse.from(saved));
        return RefreshTokenResponse.from(saved);
    }

    @Transactional
    public RefreshTokenResponse update(UUID id, RefreshTokenUpdateRequest request) {
        RefreshToken entity = require(id);
        references.require("User", "userId", request.userId());
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
        counts.invalidate(ENTITY, entity.getTenantId());
        events.publish("identity", "RefreshTokenDeleted", id, null);
    }

    private RefreshToken require(UUID id) {
        return repository.findByIdAndTenantId(id, TenantContext.requireTenantId())
                .orElseThrow(() -> new ResourceNotFoundException(RESOURCE, id));
    }
}
