package com.smartseason.media.service;

import com.smartseason.media.domain.MediaAsset;
import com.smartseason.media.platform.CountCache;
import com.smartseason.media.platform.CountCache;
import com.smartseason.media.platform.EventPublisher;
import com.smartseason.media.platform.PageResponse;
import com.smartseason.media.platform.ResourceNotFoundException;
import com.smartseason.media.platform.TenantContext;
import com.smartseason.media.repo.MediaAssetRepository;
import com.smartseason.media.web.dto.MediaAssetCreateRequest;
import com.smartseason.media.web.dto.MediaAssetResponse;
import com.smartseason.media.web.dto.MediaAssetUpdateRequest;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class MediaAssetService {

    private static final String RESOURCE = "MediaAsset";
    private static final String ENTITY = "media_assets";

    private final MediaAssetRepository repository;
    private final EventPublisher events;
    private final CountCache counts;

    public MediaAssetService(MediaAssetRepository repository, EventPublisher events, CountCache counts) {
        this.repository = repository;
        this.events = events;
        this.counts = counts;
    }

    public PageResponse<MediaAssetResponse> list(Pageable pageable) {
        UUID tenantId = TenantContext.requireTenantId();

        return PageResponse.of(
                repository.findAllByTenantId(tenantId, pageable).map(MediaAssetResponse::from),
                counts.total(ENTITY, tenantId, () -> repository.countByTenantId(tenantId)));
    }

    public MediaAssetResponse get(UUID id) {
        return MediaAssetResponse.from(require(id));
    }

    public long count() {
        return repository.countByTenantId(TenantContext.requireTenantId());
    }

    @Transactional
    public MediaAssetResponse create(MediaAssetCreateRequest request) {
        MediaAsset entity = new MediaAsset();
        entity.setTenantId(TenantContext.requireTenantId());
        entity.setStorageKey(request.storageKey());
        entity.setOriginalFilename(request.originalFilename());
        entity.setContentType(request.contentType());
        entity.setSizeBytes(request.sizeBytes());
        entity.setChecksum(request.checksum());
        entity.setOwnerUserId(request.ownerUserId());
        entity.setContext(request.context());
        entity.setContextRef(request.contextRef());
        entity.setWidth(request.width());
        entity.setHeight(request.height());
        entity.setDurationSeconds(request.durationSeconds());
        entity.setPerceptualHash(request.perceptualHash());
        entity.setExifTimestamp(request.exifTimestamp());
        entity.setExifLatitude(request.exifLatitude());
        entity.setExifLongitude(request.exifLongitude());
        entity.setPublicUrl(request.publicUrl());
        entity.setVirusScanned(request.virusScanned());
        entity.setVirusClean(request.virusClean());
        entity.setStatus(request.status());

        MediaAsset saved = repository.save(entity);
        counts.invalidate(ENTITY, saved.getTenantId());
        events.publish("platform", "MediaAssetCreated", saved.getId(), MediaAssetResponse.from(saved));
        return MediaAssetResponse.from(saved);
    }

    @Transactional
    public MediaAssetResponse update(UUID id, MediaAssetUpdateRequest request) {
        MediaAsset entity = require(id);
        if (request.storageKey() != null) {
            entity.setStorageKey(request.storageKey());
        }
        if (request.originalFilename() != null) {
            entity.setOriginalFilename(request.originalFilename());
        }
        if (request.contentType() != null) {
            entity.setContentType(request.contentType());
        }
        if (request.sizeBytes() != null) {
            entity.setSizeBytes(request.sizeBytes());
        }
        if (request.checksum() != null) {
            entity.setChecksum(request.checksum());
        }
        if (request.ownerUserId() != null) {
            entity.setOwnerUserId(request.ownerUserId());
        }
        if (request.context() != null) {
            entity.setContext(request.context());
        }
        if (request.contextRef() != null) {
            entity.setContextRef(request.contextRef());
        }
        if (request.width() != null) {
            entity.setWidth(request.width());
        }
        if (request.height() != null) {
            entity.setHeight(request.height());
        }
        if (request.durationSeconds() != null) {
            entity.setDurationSeconds(request.durationSeconds());
        }
        if (request.perceptualHash() != null) {
            entity.setPerceptualHash(request.perceptualHash());
        }
        if (request.exifTimestamp() != null) {
            entity.setExifTimestamp(request.exifTimestamp());
        }
        if (request.exifLatitude() != null) {
            entity.setExifLatitude(request.exifLatitude());
        }
        if (request.exifLongitude() != null) {
            entity.setExifLongitude(request.exifLongitude());
        }
        if (request.publicUrl() != null) {
            entity.setPublicUrl(request.publicUrl());
        }
        if (request.virusScanned() != null) {
            entity.setVirusScanned(request.virusScanned());
        }
        if (request.virusClean() != null) {
            entity.setVirusClean(request.virusClean());
        }
        if (request.status() != null) {
            entity.setStatus(request.status());
        }

        MediaAsset saved = repository.save(entity);
        events.publish("platform", "MediaAssetUpdated", saved.getId(), MediaAssetResponse.from(saved));
        return MediaAssetResponse.from(saved);
    }

    @Transactional
    public void delete(UUID id) {
        MediaAsset entity = require(id);
        repository.delete(entity);
        counts.invalidate(ENTITY, entity.getTenantId());
        events.publish("platform", "MediaAssetDeleted", id, null);
    }

    private MediaAsset require(UUID id) {
        return repository.findByIdAndTenantId(id, TenantContext.requireTenantId())
                .orElseThrow(() -> new ResourceNotFoundException(RESOURCE, id));
    }
}
