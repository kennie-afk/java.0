package com.smartseason.notification.service;

import com.smartseason.notification.domain.UssdSession;
import com.smartseason.notification.platform.CountCache;
import com.smartseason.notification.platform.CountCache;
import com.smartseason.notification.platform.EventPublisher;
import com.smartseason.notification.platform.ReferenceChecker;
import com.smartseason.notification.platform.Cursor;
import com.smartseason.notification.platform.CursorPage;
import com.smartseason.notification.platform.PageResponse;
import com.smartseason.notification.platform.ResourceNotFoundException;
import com.smartseason.notification.platform.TenantContext;
import com.smartseason.notification.repo.UssdSessionRepository;
import com.smartseason.notification.web.dto.UssdSessionCreateRequest;
import com.smartseason.notification.web.dto.UssdSessionResponse;
import com.smartseason.notification.web.dto.UssdSessionUpdateRequest;
import com.smartseason.notification.platform.ListFilter;
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
public class UssdSessionService {

    private static final String RESOURCE = "UssdSession";
    private static final String ENTITY = "ussd_sessions";

    private static final Map<String, Class<?>> FILTERABLE = Map.ofEntries(
            Map.entry("sessionId", String.class),
            Map.entry("phoneNumber", String.class),
            Map.entry("serviceCode", String.class),
            Map.entry("currentMenu", String.class),
            Map.entry("menuStack", String.class),
            Map.entry("status", UssdSession.Status.class));

    private static final List<String> SEARCHABLE = List.of("sessionId", "phoneNumber", "serviceCode", "currentMenu", "menuStack");

    private final UssdSessionRepository repository;
    private final EventPublisher events;
    private final CountCache counts;
    private final ReferenceChecker references;

    public UssdSessionService(UssdSessionRepository repository, EventPublisher events, CountCache counts,
            ReferenceChecker references) {
        this.repository = repository;
        this.events = events;
        this.counts = counts;
        this.references = references;
    }

    public PageResponse<UssdSessionResponse> list(Pageable pageable, Map<String, String> params) {
        if (ListFilter.isEmpty(params)) {
            return list(pageable);
        }
        UUID tenantId = TenantContext.requireTenantId();
        var spec = ListFilter.<UssdSession>of(tenantId, params, FILTERABLE, SEARCHABLE);
        return PageResponse.from(repository.findAll(spec, pageable).map(UssdSessionResponse::from));
    }

    public PageResponse<UssdSessionResponse> list(Pageable pageable) {
        UUID tenantId = TenantContext.requireTenantId();

        return PageResponse.of(
                repository.findAllByTenantId(tenantId, pageable).map(UssdSessionResponse::from),
                counts.total(ENTITY, tenantId, () -> repository.countByTenantId(tenantId)));
    }

    public CursorPage<UssdSessionResponse> listByCursor(String cursor, int size) {
        UUID tenantId = TenantContext.requireTenantId();

        Pageable pageable = PageRequest.of(0, Math.max(1, Math.min(size, 200)));

        Cursor from = Cursor.decode(cursor);
        Slice<UssdSession> slice = (from == null)
                ? repository.findAllByTenantIdOrderByCreatedAtDescIdDesc(tenantId, pageable)
                : repository.findAfterCursor(tenantId, from.createdAt(), from.id(), pageable);

        return CursorPage.of(
                slice,
                slice.getContent().stream().map(UssdSessionResponse::from).toList(),
                e -> new Cursor(e.getCreatedAt(), e.getId()));
    }

    public UssdSessionResponse get(UUID id) {
        return UssdSessionResponse.from(require(id));
    }

    public long count() {
        return repository.countByTenantId(TenantContext.requireTenantId());
    }

    @Transactional
    public UssdSessionResponse create(UssdSessionCreateRequest request) {
        UssdSession entity = new UssdSession();
        entity.setTenantId(TenantContext.requireTenantId());
        entity.setSessionId(request.sessionId());
        entity.setPhoneNumber(request.phoneNumber());
        entity.setServiceCode(request.serviceCode());
        entity.setCurrentMenu(request.currentMenu());
        entity.setMenuStack(request.menuStack());
        entity.setContext(request.context());
        entity.setStartedAt(request.startedAt());
        entity.setLastInputAt(request.lastInputAt());
        entity.setEndedAt(request.endedAt());
        entity.setStatus(request.status());
        entity.setHops(request.hops());

        UssdSession saved = repository.save(entity);
        counts.invalidate(ENTITY, saved.getTenantId());
        events.publish("platform", "UssdSessionCreated", saved.getId(), UssdSessionResponse.from(saved));
        return UssdSessionResponse.from(saved);
    }

    @Transactional
    public UssdSessionResponse update(UUID id, UssdSessionUpdateRequest request) {
        UssdSession entity = require(id);
        if (request.sessionId() != null) {
            entity.setSessionId(request.sessionId());
        }
        if (request.phoneNumber() != null) {
            entity.setPhoneNumber(request.phoneNumber());
        }
        if (request.serviceCode() != null) {
            entity.setServiceCode(request.serviceCode());
        }
        if (request.currentMenu() != null) {
            entity.setCurrentMenu(request.currentMenu());
        }
        if (request.menuStack() != null) {
            entity.setMenuStack(request.menuStack());
        }
        if (request.context() != null) {
            entity.setContext(request.context());
        }
        if (request.startedAt() != null) {
            entity.setStartedAt(request.startedAt());
        }
        if (request.lastInputAt() != null) {
            entity.setLastInputAt(request.lastInputAt());
        }
        if (request.endedAt() != null) {
            entity.setEndedAt(request.endedAt());
        }
        if (request.status() != null) {
            entity.setStatus(request.status());
        }
        if (request.hops() != null) {
            entity.setHops(request.hops());
        }

        UssdSession saved = repository.save(entity);
        events.publish("platform", "UssdSessionUpdated", saved.getId(), UssdSessionResponse.from(saved));
        return UssdSessionResponse.from(saved);
    }

    @Transactional
    public void delete(UUID id) {
        UssdSession entity = require(id);
        repository.delete(entity);
        counts.invalidate(ENTITY, entity.getTenantId());
        events.publish("platform", "UssdSessionDeleted", id, null);
    }

    private UssdSession require(UUID id) {
        return repository.findByIdAndTenantId(id, TenantContext.requireTenantId())
                .orElseThrow(() -> new ResourceNotFoundException(RESOURCE, id));
    }
}
