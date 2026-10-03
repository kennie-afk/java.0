package com.smartseason.analytics.service;

import com.smartseason.analytics.domain.Report;
import com.smartseason.analytics.platform.CountCache;
import com.smartseason.analytics.platform.CountCache;
import com.smartseason.analytics.platform.EventPublisher;
import com.smartseason.analytics.platform.ReferenceChecker;
import com.smartseason.analytics.platform.Cursor;
import com.smartseason.analytics.platform.CursorPage;
import com.smartseason.analytics.platform.PageResponse;
import com.smartseason.analytics.platform.ResourceNotFoundException;
import com.smartseason.analytics.platform.TenantContext;
import com.smartseason.analytics.repo.ReportRepository;
import com.smartseason.analytics.web.dto.ReportCreateRequest;
import com.smartseason.analytics.web.dto.ReportResponse;
import com.smartseason.analytics.web.dto.ReportUpdateRequest;
import com.smartseason.analytics.platform.ListFilter;
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
public class ReportService {

    private static final String RESOURCE = "Report";
    private static final String ENTITY = "reports";

    private static final Map<String, Class<?>> FILTERABLE = Map.ofEntries(
            Map.entry("code", String.class),
            Map.entry("name", String.class),
            Map.entry("category", String.class),
            Map.entry("schedule", String.class),
            Map.entry("format", Report.Format.class),
            Map.entry("enabled", Boolean.class),
            Map.entry("ownerUserId", UUID.class));

    private static final List<String> SEARCHABLE = List.of("code", "name", "category", "schedule");

    private final ReportRepository repository;
    private final EventPublisher events;
    private final CountCache counts;
    private final ReferenceChecker references;

    public ReportService(ReportRepository repository, EventPublisher events, CountCache counts,
            ReferenceChecker references) {
        this.repository = repository;
        this.events = events;
        this.counts = counts;
        this.references = references;
    }

    public PageResponse<ReportResponse> list(Pageable pageable, Map<String, String> params) {
        if (ListFilter.isEmpty(params)) {
            return list(pageable);
        }
        UUID tenantId = TenantContext.requireTenantId();
        var spec = ListFilter.<Report>of(tenantId, params, FILTERABLE, SEARCHABLE);
        return PageResponse.from(repository.findAll(spec, pageable).map(ReportResponse::from));
    }

    public PageResponse<ReportResponse> list(Pageable pageable) {
        UUID tenantId = TenantContext.requireTenantId();

        return PageResponse.of(
                repository.findAllByTenantId(tenantId, pageable).map(ReportResponse::from),
                counts.total(ENTITY, tenantId, () -> repository.countByTenantId(tenantId)));
    }

    public CursorPage<ReportResponse> listByCursor(String cursor, int size) {
        UUID tenantId = TenantContext.requireTenantId();

        Pageable pageable = PageRequest.of(0, Math.max(1, Math.min(size, 200)));

        Cursor from = Cursor.decode(cursor);
        Slice<Report> slice = (from == null)
                ? repository.findAllByTenantIdOrderByCreatedAtDescIdDesc(tenantId, pageable)
                : repository.findAfterCursor(tenantId, from.createdAt(), from.id(), pageable);

        return CursorPage.of(
                slice,
                slice.getContent().stream().map(ReportResponse::from).toList(),
                e -> new Cursor(e.getCreatedAt(), e.getId()));
    }

    public ReportResponse get(UUID id) {
        return ReportResponse.from(require(id));
    }

    public long count() {
        return repository.countByTenantId(TenantContext.requireTenantId());
    }

    @Transactional
    public ReportResponse create(ReportCreateRequest request) {
        Report entity = new Report();
        entity.setTenantId(TenantContext.requireTenantId());
        entity.setCode(request.code());
        entity.setName(request.name());
        entity.setDescription(request.description());
        entity.setCategory(request.category());
        entity.setQuerySpec(request.querySpec());
        entity.setSchedule(request.schedule());
        entity.setFormat(request.format());
        entity.setEnabled(request.enabled());
        entity.setOwnerUserId(request.ownerUserId());

        Report saved = repository.save(entity);
        counts.invalidate(ENTITY, saved.getTenantId());
        events.publish("platform", "ReportCreated", saved.getId(), ReportResponse.from(saved));
        return ReportResponse.from(saved);
    }

    @Transactional
    public ReportResponse update(UUID id, ReportUpdateRequest request) {
        Report entity = require(id);
        if (request.code() != null) {
            entity.setCode(request.code());
        }
        if (request.name() != null) {
            entity.setName(request.name());
        }
        if (request.description() != null) {
            entity.setDescription(request.description());
        }
        if (request.category() != null) {
            entity.setCategory(request.category());
        }
        if (request.querySpec() != null) {
            entity.setQuerySpec(request.querySpec());
        }
        if (request.schedule() != null) {
            entity.setSchedule(request.schedule());
        }
        if (request.format() != null) {
            entity.setFormat(request.format());
        }
        if (request.enabled() != null) {
            entity.setEnabled(request.enabled());
        }
        if (request.ownerUserId() != null) {
            entity.setOwnerUserId(request.ownerUserId());
        }

        Report saved = repository.save(entity);
        events.publish("platform", "ReportUpdated", saved.getId(), ReportResponse.from(saved));
        return ReportResponse.from(saved);
    }

    @Transactional
    public void delete(UUID id) {
        Report entity = require(id);
        repository.delete(entity);
        counts.invalidate(ENTITY, entity.getTenantId());
        events.publish("platform", "ReportDeleted", id, null);
    }

    private Report require(UUID id) {
        return repository.findByIdAndTenantId(id, TenantContext.requireTenantId())
                .orElseThrow(() -> new ResourceNotFoundException(RESOURCE, id));
    }
}
