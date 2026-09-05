package com.smartseason.notification.service;

import com.smartseason.notification.domain.UssdSession;
import com.smartseason.notification.platform.EventPublisher;
import com.smartseason.notification.platform.PageResponse;
import com.smartseason.notification.platform.ResourceNotFoundException;
import com.smartseason.notification.platform.TenantContext;
import com.smartseason.notification.repo.UssdSessionRepository;
import com.smartseason.notification.web.dto.UssdSessionCreateRequest;
import com.smartseason.notification.web.dto.UssdSessionResponse;
import com.smartseason.notification.web.dto.UssdSessionUpdateRequest;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class UssdSessionService {

    private static final String RESOURCE = "UssdSession";

    private final UssdSessionRepository repository;
    private final EventPublisher events;

    public UssdSessionService(UssdSessionRepository repository, EventPublisher events) {
        this.repository = repository;
        this.events = events;
    }

    public PageResponse<UssdSessionResponse> list(Pageable pageable) {
        return PageResponse.from(
                repository.findAllByTenantId(TenantContext.requireTenantId(), pageable)
                        .map(UssdSessionResponse::from));
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
        events.publish("platform", "UssdSessionDeleted", id, null);
    }

    private UssdSession require(UUID id) {
        return repository.findByIdAndTenantId(id, TenantContext.requireTenantId())
                .orElseThrow(() -> new ResourceNotFoundException(RESOURCE, id));
    }
}
