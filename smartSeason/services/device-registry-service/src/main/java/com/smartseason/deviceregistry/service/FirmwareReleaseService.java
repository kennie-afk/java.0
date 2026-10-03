package com.smartseason.deviceregistry.service;

import com.smartseason.deviceregistry.domain.FirmwareRelease;
import com.smartseason.deviceregistry.platform.CountCache;
import com.smartseason.deviceregistry.platform.CountCache;
import com.smartseason.deviceregistry.platform.EventPublisher;
import com.smartseason.deviceregistry.platform.ReferenceChecker;
import com.smartseason.deviceregistry.platform.Cursor;
import com.smartseason.deviceregistry.platform.CursorPage;
import com.smartseason.deviceregistry.platform.PageResponse;
import com.smartseason.deviceregistry.platform.ResourceNotFoundException;
import com.smartseason.deviceregistry.platform.TenantContext;
import com.smartseason.deviceregistry.repo.FirmwareReleaseRepository;
import com.smartseason.deviceregistry.web.dto.FirmwareReleaseCreateRequest;
import com.smartseason.deviceregistry.web.dto.FirmwareReleaseResponse;
import com.smartseason.deviceregistry.web.dto.FirmwareReleaseUpdateRequest;
import com.smartseason.deviceregistry.platform.ListFilter;
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
public class FirmwareReleaseService {

    private static final String RESOURCE = "FirmwareRelease";
    private static final String ENTITY = "firmware_releases";

    private static final Map<String, Class<?>> FILTERABLE = Map.ofEntries(
            Map.entry("deviceType", String.class),
            Map.entry("releaseVersion", String.class),
            Map.entry("artifactUrl", String.class),
            Map.entry("checksum", String.class),
            Map.entry("mandatory", Boolean.class));

    private static final List<String> SEARCHABLE = List.of("deviceType", "releaseVersion", "artifactUrl", "checksum");

    private final FirmwareReleaseRepository repository;
    private final EventPublisher events;
    private final CountCache counts;
    private final ReferenceChecker references;

    public FirmwareReleaseService(FirmwareReleaseRepository repository, EventPublisher events, CountCache counts,
            ReferenceChecker references) {
        this.repository = repository;
        this.events = events;
        this.counts = counts;
        this.references = references;
    }

    public PageResponse<FirmwareReleaseResponse> list(Pageable pageable, Map<String, String> params) {
        if (ListFilter.isEmpty(params)) {
            return list(pageable);
        }
        UUID tenantId = TenantContext.requireTenantId();
        var spec = ListFilter.<FirmwareRelease>of(tenantId, params, FILTERABLE, SEARCHABLE);
        return PageResponse.from(repository.findAll(spec, pageable).map(FirmwareReleaseResponse::from));
    }

    public PageResponse<FirmwareReleaseResponse> list(Pageable pageable) {
        UUID tenantId = TenantContext.requireTenantId();

        return PageResponse.of(
                repository.findAllByTenantId(tenantId, pageable).map(FirmwareReleaseResponse::from),
                counts.total(ENTITY, tenantId, () -> repository.countByTenantId(tenantId)));
    }

    public CursorPage<FirmwareReleaseResponse> listByCursor(String cursor, int size) {
        UUID tenantId = TenantContext.requireTenantId();

        Pageable pageable = PageRequest.of(0, Math.max(1, Math.min(size, 200)));

        Cursor from = Cursor.decode(cursor);
        Slice<FirmwareRelease> slice = (from == null)
                ? repository.findAllByTenantIdOrderByCreatedAtDescIdDesc(tenantId, pageable)
                : repository.findAfterCursor(tenantId, from.createdAt(), from.id(), pageable);

        return CursorPage.of(
                slice,
                slice.getContent().stream().map(FirmwareReleaseResponse::from).toList(),
                e -> new Cursor(e.getCreatedAt(), e.getId()));
    }

    public FirmwareReleaseResponse get(UUID id) {
        return FirmwareReleaseResponse.from(require(id));
    }

    public long count() {
        return repository.countByTenantId(TenantContext.requireTenantId());
    }

    @Transactional
    public FirmwareReleaseResponse create(FirmwareReleaseCreateRequest request) {
        FirmwareRelease entity = new FirmwareRelease();
        entity.setTenantId(TenantContext.requireTenantId());
        entity.setDeviceType(request.deviceType());
        entity.setReleaseVersion(request.releaseVersion());
        entity.setArtifactUrl(request.artifactUrl());
        entity.setChecksum(request.checksum());
        entity.setReleaseNotes(request.releaseNotes());
        entity.setMandatory(request.mandatory());
        entity.setPublishedAt(request.publishedAt());

        FirmwareRelease saved = repository.save(entity);
        counts.invalidate(ENTITY, saved.getTenantId());
        events.publish("iot", "FirmwareReleaseCreated", saved.getId(), FirmwareReleaseResponse.from(saved));
        return FirmwareReleaseResponse.from(saved);
    }

    @Transactional
    public FirmwareReleaseResponse update(UUID id, FirmwareReleaseUpdateRequest request) {
        FirmwareRelease entity = require(id);
        if (request.deviceType() != null) {
            entity.setDeviceType(request.deviceType());
        }
        if (request.releaseVersion() != null) {
            entity.setReleaseVersion(request.releaseVersion());
        }
        if (request.artifactUrl() != null) {
            entity.setArtifactUrl(request.artifactUrl());
        }
        if (request.checksum() != null) {
            entity.setChecksum(request.checksum());
        }
        if (request.releaseNotes() != null) {
            entity.setReleaseNotes(request.releaseNotes());
        }
        if (request.mandatory() != null) {
            entity.setMandatory(request.mandatory());
        }
        if (request.publishedAt() != null) {
            entity.setPublishedAt(request.publishedAt());
        }

        FirmwareRelease saved = repository.save(entity);
        events.publish("iot", "FirmwareReleaseUpdated", saved.getId(), FirmwareReleaseResponse.from(saved));
        return FirmwareReleaseResponse.from(saved);
    }

    @Transactional
    public void delete(UUID id) {
        FirmwareRelease entity = require(id);
        repository.delete(entity);
        counts.invalidate(ENTITY, entity.getTenantId());
        events.publish("iot", "FirmwareReleaseDeleted", id, null);
    }

    private FirmwareRelease require(UUID id) {
        return repository.findByIdAndTenantId(id, TenantContext.requireTenantId())
                .orElseThrow(() -> new ResourceNotFoundException(RESOURCE, id));
    }
}
