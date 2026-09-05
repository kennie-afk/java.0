package com.smartseason.notification.service;

import com.smartseason.notification.domain.NotificationTemplate;
import com.smartseason.notification.platform.EventPublisher;
import com.smartseason.notification.platform.PageResponse;
import com.smartseason.notification.platform.ResourceNotFoundException;
import com.smartseason.notification.platform.TenantContext;
import com.smartseason.notification.repo.NotificationTemplateRepository;
import com.smartseason.notification.web.dto.NotificationTemplateCreateRequest;
import com.smartseason.notification.web.dto.NotificationTemplateResponse;
import com.smartseason.notification.web.dto.NotificationTemplateUpdateRequest;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class NotificationTemplateService {

    private static final String RESOURCE = "NotificationTemplate";

    private final NotificationTemplateRepository repository;
    private final EventPublisher events;

    public NotificationTemplateService(NotificationTemplateRepository repository, EventPublisher events) {
        this.repository = repository;
        this.events = events;
    }

    public PageResponse<NotificationTemplateResponse> list(Pageable pageable) {
        return PageResponse.from(
                repository.findAllByTenantId(TenantContext.requireTenantId(), pageable)
                        .map(NotificationTemplateResponse::from));
    }

    public NotificationTemplateResponse get(UUID id) {
        return NotificationTemplateResponse.from(require(id));
    }

    public long count() {
        return repository.countByTenantId(TenantContext.requireTenantId());
    }

    @Transactional
    public NotificationTemplateResponse create(NotificationTemplateCreateRequest request) {
        NotificationTemplate entity = new NotificationTemplate();
        entity.setTenantId(TenantContext.requireTenantId());
        entity.setCode(request.code());
        entity.setChannel(request.channel());
        entity.setLocale(request.locale());
        entity.setSubject(request.subject());
        entity.setBody(request.body());
        entity.setVariables(request.variables());
        entity.setActive(request.active());
        entity.setRevision(request.revision());

        NotificationTemplate saved = repository.save(entity);
        events.publish("platform", "NotificationTemplateCreated", saved.getId(), NotificationTemplateResponse.from(saved));
        return NotificationTemplateResponse.from(saved);
    }

    @Transactional
    public NotificationTemplateResponse update(UUID id, NotificationTemplateUpdateRequest request) {
        NotificationTemplate entity = require(id);
        if (request.code() != null) {
            entity.setCode(request.code());
        }
        if (request.channel() != null) {
            entity.setChannel(request.channel());
        }
        if (request.locale() != null) {
            entity.setLocale(request.locale());
        }
        if (request.subject() != null) {
            entity.setSubject(request.subject());
        }
        if (request.body() != null) {
            entity.setBody(request.body());
        }
        if (request.variables() != null) {
            entity.setVariables(request.variables());
        }
        if (request.active() != null) {
            entity.setActive(request.active());
        }
        if (request.revision() != null) {
            entity.setRevision(request.revision());
        }

        NotificationTemplate saved = repository.save(entity);
        events.publish("platform", "NotificationTemplateUpdated", saved.getId(), NotificationTemplateResponse.from(saved));
        return NotificationTemplateResponse.from(saved);
    }

    @Transactional
    public void delete(UUID id) {
        NotificationTemplate entity = require(id);
        repository.delete(entity);
        events.publish("platform", "NotificationTemplateDeleted", id, null);
    }

    private NotificationTemplate require(UUID id) {
        return repository.findByIdAndTenantId(id, TenantContext.requireTenantId())
                .orElseThrow(() -> new ResourceNotFoundException(RESOURCE, id));
    }
}
