package com.smartseason.media.service;

import com.smartseason.media.domain.MediaVariant;
import com.smartseason.media.platform.CountCache;
import com.smartseason.media.platform.CountCache;
import com.smartseason.media.platform.EventPublisher;
import com.smartseason.media.platform.ReferenceChecker;
import com.smartseason.media.platform.Cursor;
import com.smartseason.media.platform.CursorPage;
import com.smartseason.media.platform.PageResponse;
import com.smartseason.media.platform.ResourceNotFoundException;
import com.smartseason.media.platform.TenantContext;
import com.smartseason.media.repo.MediaVariantRepository;
import com.smartseason.media.web.dto.MediaVariantCreateRequest;
import com.smartseason.media.web.dto.MediaVariantResponse;
import com.smartseason.media.web.dto.MediaVariantUpdateRequest;
import com.smartseason.media.platform.ListFilter;
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
public class MediaVariantService {

    private static final String RESOURCE = "MediaVariant";
    private static final String ENTITY = "media_variants";

    private static final Map<String, Class<?>> FILTERABLE = Map.ofEntries(
            Map.entry("assetId", UUID.class),
            Map.entry("variantName", String.class),
            Map.entry("contentType", String.class),
            Map.entry("publicUrl", String.class),
            Map.entry("storageKey", String.class));

    private static final List<String> SEARCHABLE = List.of("variantName", "contentType", "publicUrl", "storageKey");

    private final MediaVariantRepository repository;
    private final EventPublisher events;
    private final CountCache counts;
    private final ReferenceChecker references;

    public MediaVariantService(MediaVariantRepository repository, EventPublisher events, CountCache counts,
            ReferenceChecker references) {
        this.repository = repository;
        this.events = events;
        this.counts = counts;
        this.references = references;
    }

    public PageResponse<MediaVariantResponse> list(Pageable pageable, Map<String, String> params) {
        if (ListFilter.isEmpty(params)) {
            return list(pageable);
        }
        UUID tenantId = TenantContext.requireTenantId();
        var spec = ListFilter.<MediaVariant>of(tenantId, params, FILTERABLE, SEARCHABLE);
        return PageResponse.from(repository.findAll(spec, pageable).map(MediaVariantResponse::from));
    }

    public PageResponse<MediaVariantResponse> list(Pageable pageable) {
        UUID tenantId = TenantContext.requireTenantId();

        return PageResponse.of(
                repository.findAllByTenantId(tenantId, pageable).map(MediaVariantResponse::from),
                counts.total(ENTITY, tenantId, () -> repository.countByTenantId(tenantId)));
    }

    public CursorPage<MediaVariantResponse> listByCursor(String cursor, int size) {
        UUID tenantId = TenantContext.requireTenantId();

        Pageable pageable = PageRequest.of(0, Math.max(1, Math.min(size, 200)));

        Cursor from = Cursor.decode(cursor);
        Slice<MediaVariant> slice = (from == null)
                ? repository.findAllByTenantIdOrderByCreatedAtDescIdDesc(tenantId, pageable)
                : repository.findAfterCursor(tenantId, from.createdAt(), from.id(), pageable);

        return CursorPage.of(
                slice,
                slice.getContent().stream().map(MediaVariantResponse::from).toList(),
                e -> new Cursor(e.getCreatedAt(), e.getId()));
    }

    public MediaVariantResponse get(UUID id) {
        return MediaVariantResponse.from(require(id));
    }

    public long count() {
        return repository.countByTenantId(TenantContext.requireTenantId());
    }

    @Transactional
    public MediaVariantResponse create(MediaVariantCreateRequest request) {
        MediaVariant entity = new MediaVariant();
        entity.setTenantId(TenantContext.requireTenantId());

        references.require("MediaAsset", "assetId", request.assetId());
        entity.setAssetId(request.assetId());
        entity.setVariantName(request.variantName());
        entity.setWidth(request.width());
        entity.setHeight(request.height());
        entity.setSizeBytes(request.sizeBytes());
        entity.setContentType(request.contentType());
        entity.setPublicUrl(request.publicUrl());
        entity.setStorageKey(TenantContext.requireTenantId() + "/media_variants/" + UUID.randomUUID());

        MediaVariant saved = repository.save(entity);
        counts.invalidate(ENTITY, saved.getTenantId());
        events.publish("platform", "MediaVariantCreated", saved.getId(), MediaVariantResponse.from(saved));
        return MediaVariantResponse.from(saved);
    }

    @Transactional
    public MediaVariantResponse update(UUID id, MediaVariantUpdateRequest request) {
        MediaVariant entity = require(id);
        references.require("MediaAsset", "assetId", request.assetId());
        if (request.assetId() != null) {
            entity.setAssetId(request.assetId());
        }
        if (request.variantName() != null) {
            entity.setVariantName(request.variantName());
        }
        if (request.width() != null) {
            entity.setWidth(request.width());
        }
        if (request.height() != null) {
            entity.setHeight(request.height());
        }
        if (request.sizeBytes() != null) {
            entity.setSizeBytes(request.sizeBytes());
        }
        if (request.contentType() != null) {
            entity.setContentType(request.contentType());
        }
        if (request.publicUrl() != null) {
            entity.setPublicUrl(request.publicUrl());
        }

        MediaVariant saved = repository.save(entity);
        events.publish("platform", "MediaVariantUpdated", saved.getId(), MediaVariantResponse.from(saved));
        return MediaVariantResponse.from(saved);
    }

    @Transactional
    public void delete(UUID id) {
        MediaVariant entity = require(id);
        repository.delete(entity);
        counts.invalidate(ENTITY, entity.getTenantId());
        events.publish("platform", "MediaVariantDeleted", id, null);
    }

    private MediaVariant require(UUID id) {
        return repository.findByIdAndTenantId(id, TenantContext.requireTenantId())
                .orElseThrow(() -> new ResourceNotFoundException(RESOURCE, id));
    }
}
