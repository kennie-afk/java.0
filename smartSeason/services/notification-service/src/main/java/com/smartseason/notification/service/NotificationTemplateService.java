package com.smartseason.notification.service;

import com.smartseason.notification.domain.NotificationTemplate;
import com.smartseason.notification.platform.CountCache;
import com.smartseason.notification.platform.CountCache;
import com.smartseason.notification.platform.EventPublisher;
import com.smartseason.notification.platform.ReferenceChecker;
import com.smartseason.notification.platform.Cursor;
import com.smartseason.notification.platform.CursorPage;
import com.smartseason.notification.platform.PageResponse;
import com.smartseason.notification.platform.ResourceNotFoundException;
import com.smartseason.notification.platform.TenantContext;
import com.smartseason.notification.repo.NotificationTemplateRepository;
import com.smartseason.notification.web.dto.NotificationTemplateCreateRequest;
import com.smartseason.notification.web.dto.NotificationTemplateResponse;
import com.smartseason.notification.web.dto.NotificationTemplateUpdateRequest;
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
public class NotificationTemplateService {

    private static final String RESOURCE = "NotificationTemplate";
    private static final String ENTITY = "notification_templates";

    private static final Map<String, Class<?>> FILTERABLE = Map.ofEntries(
            Map.entry("code", String.class),
            Map.entry("channel", NotificationTemplate.Channel.class),
            Map.entry("locale", String.class),
            Map.entry("subject", String.class),
            Map.entry("variables", String.class),
            Map.entry("active", Boolean.class));

    private static final List<String> SEARCHABLE = List.of("code", "locale", "subject", "variables");

    private final NotificationTemplateRepository repository;
    private final EventPublisher events;
    private final CountCache counts;
    private final ReferenceChecker references;

    public NotificationTemplateService(NotificationTemplateRepository repository, EventPublisher events, CountCache counts,
            ReferenceChecker references) {
        this.repository = repository;
        this.events = events;
        this.counts = counts;
        this.references = references;
    }

    public PageResponse<NotificationTemplateResponse> list(Pageable pageable, Map<String, String> params) {
        if (ListFilter.isEmpty(params)) {
            return list(pageable);
        }
        UUID tenantId = TenantContext.requireTenantId();
        var spec = ListFilter.<NotificationTemplate>of(tenantId, params, FILTERABLE, SEARCHABLE);
        return PageResponse.from(repository.findAll(spec, pageable).map(NotificationTemplateResponse::from));
    }

    public PageResponse<NotificationTemplateResponse> list(Pageable pageable) {
        UUID tenantId = TenantContext.requireTenantId();

        return PageResponse.of(
                repository.findAllByTenantId(tenantId, pageable).map(NotificationTemplateResponse::from),
                counts.total(ENTITY, tenantId, () -> repository.countByTenantId(tenantId)));
    }

    public CursorPage<NotificationTemplateResponse> listByCursor(String cursor, int size) {
        UUID tenantId = TenantContext.requireTenantId();

        Pageable pageable = PageRequest.of(0, Math.max(1, Math.min(size, 200)));

        Cursor from = Cursor.decode(cursor);
        Slice<NotificationTemplate> slice = (from == null)
                ? repository.findAllByTenantIdOrderByCreatedAtDescIdDesc(tenantId, pageable)
                : repository.findAfterCursor(tenantId, from.createdAt(), from.id(), pageable);

        return CursorPage.of(
                slice,
                slice.getContent().stream().map(NotificationTemplateResponse::from).toList(),
                e -> new Cursor(e.getCreatedAt(), e.getId()));
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
        counts.invalidate(ENTITY, saved.getTenantId());
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
        counts.invalidate(ENTITY, entity.getTenantId());
        events.publish("platform", "NotificationTemplateDeleted", id, null);
    }

    private NotificationTemplate require(UUID id) {
        return repository.findByIdAndTenantId(id, TenantContext.requireTenantId())
                .orElseThrow(() -> new ResourceNotFoundException(RESOURCE, id));
    }
}
