package com.smartseason.payment.service;

import com.smartseason.payment.domain.ProviderCallback;
import com.smartseason.payment.platform.CountCache;
import com.smartseason.payment.platform.CountCache;
import com.smartseason.payment.platform.EventPublisher;
import com.smartseason.payment.platform.ReferenceChecker;
import com.smartseason.payment.platform.Cursor;
import com.smartseason.payment.platform.CursorPage;
import com.smartseason.payment.platform.PageResponse;
import com.smartseason.payment.platform.ResourceNotFoundException;
import com.smartseason.payment.platform.TenantContext;
import com.smartseason.payment.repo.ProviderCallbackRepository;
import com.smartseason.payment.web.dto.ProviderCallbackCreateRequest;
import com.smartseason.payment.web.dto.ProviderCallbackResponse;
import com.smartseason.payment.web.dto.ProviderCallbackUpdateRequest;
import com.smartseason.payment.platform.ListFilter;
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
public class ProviderCallbackService {

    private static final String RESOURCE = "ProviderCallback";
    private static final String ENTITY = "provider_callbacks";

    private static final Map<String, Class<?>> FILTERABLE = Map.ofEntries(
            Map.entry("provider", String.class),
            Map.entry("callbackType", String.class),
            Map.entry("externalRef", String.class),
            Map.entry("signature", String.class),
            Map.entry("status", ProviderCallback.Status.class));

    private static final List<String> SEARCHABLE = List.of("provider", "callbackType", "externalRef", "signature");

    private final ProviderCallbackRepository repository;
    private final EventPublisher events;
    private final CountCache counts;
    private final ReferenceChecker references;

    public ProviderCallbackService(ProviderCallbackRepository repository, EventPublisher events, CountCache counts,
            ReferenceChecker references) {
        this.repository = repository;
        this.events = events;
        this.counts = counts;
        this.references = references;
    }

    public PageResponse<ProviderCallbackResponse> list(Pageable pageable, Map<String, String> params) {
        if (ListFilter.isEmpty(params)) {
            return list(pageable);
        }
        UUID tenantId = TenantContext.requireTenantId();
        var spec = ListFilter.<ProviderCallback>of(tenantId, params, FILTERABLE, SEARCHABLE);
        return PageResponse.from(repository.findAll(spec, pageable).map(ProviderCallbackResponse::from));
    }

    public PageResponse<ProviderCallbackResponse> list(Pageable pageable) {
        UUID tenantId = TenantContext.requireTenantId();

        return PageResponse.of(
                repository.findAllByTenantId(tenantId, pageable).map(ProviderCallbackResponse::from),
                counts.total(ENTITY, tenantId, () -> repository.countByTenantId(tenantId)));
    }

    public CursorPage<ProviderCallbackResponse> listByCursor(String cursor, int size) {
        UUID tenantId = TenantContext.requireTenantId();

        Pageable pageable = PageRequest.of(0, Math.max(1, Math.min(size, 200)));

        Cursor from = Cursor.decode(cursor);
        Slice<ProviderCallback> slice = (from == null)
                ? repository.findAllByTenantIdOrderByCreatedAtDescIdDesc(tenantId, pageable)
                : repository.findAfterCursor(tenantId, from.createdAt(), from.id(), pageable);

        return CursorPage.of(
                slice,
                slice.getContent().stream().map(ProviderCallbackResponse::from).toList(),
                e -> new Cursor(e.getCreatedAt(), e.getId()));
    }

    public ProviderCallbackResponse get(UUID id) {
        return ProviderCallbackResponse.from(require(id));
    }

    public long count() {
        return repository.countByTenantId(TenantContext.requireTenantId());
    }

    @Transactional
    public ProviderCallbackResponse create(ProviderCallbackCreateRequest request) {
        ProviderCallback entity = new ProviderCallback();
        entity.setTenantId(TenantContext.requireTenantId());
        entity.setProvider(request.provider());
        entity.setCallbackType(request.callbackType());
        entity.setExternalRef(request.externalRef());
        entity.setSignature(request.signature());
        entity.setPayload(request.payload());
        entity.setReceivedAt(request.receivedAt());
        entity.setProcessedAt(request.processedAt());
        entity.setStatus(request.status());
        entity.setError(request.error());

        ProviderCallback saved = repository.save(entity);
        counts.invalidate(ENTITY, saved.getTenantId());
        events.publish("money", "ProviderCallbackCreated", saved.getId(), ProviderCallbackResponse.from(saved));
        return ProviderCallbackResponse.from(saved);
    }

    @Transactional
    public ProviderCallbackResponse update(UUID id, ProviderCallbackUpdateRequest request) {
        ProviderCallback entity = require(id);
        if (request.provider() != null) {
            entity.setProvider(request.provider());
        }
        if (request.callbackType() != null) {
            entity.setCallbackType(request.callbackType());
        }
        if (request.externalRef() != null) {
            entity.setExternalRef(request.externalRef());
        }
        if (request.signature() != null) {
            entity.setSignature(request.signature());
        }
        if (request.payload() != null) {
            entity.setPayload(request.payload());
        }
        if (request.receivedAt() != null) {
            entity.setReceivedAt(request.receivedAt());
        }
        if (request.processedAt() != null) {
            entity.setProcessedAt(request.processedAt());
        }
        if (request.status() != null) {
            entity.setStatus(request.status());
        }
        if (request.error() != null) {
            entity.setError(request.error());
        }

        ProviderCallback saved = repository.save(entity);
        events.publish("money", "ProviderCallbackUpdated", saved.getId(), ProviderCallbackResponse.from(saved));
        return ProviderCallbackResponse.from(saved);
    }

    @Transactional
    public void delete(UUID id) {
        ProviderCallback entity = require(id);
        repository.delete(entity);
        counts.invalidate(ENTITY, entity.getTenantId());
        events.publish("money", "ProviderCallbackDeleted", id, null);
    }

    private ProviderCallback require(UUID id) {
        return repository.findByIdAndTenantId(id, TenantContext.requireTenantId())
                .orElseThrow(() -> new ResourceNotFoundException(RESOURCE, id));
    }
}
