package com.smartseason.inventory.service;

import com.smartseason.inventory.domain.InputIssue;
import com.smartseason.inventory.platform.CountCache;
import com.smartseason.inventory.platform.CountCache;
import com.smartseason.inventory.platform.EventPublisher;
import com.smartseason.inventory.platform.Cursor;
import com.smartseason.inventory.platform.CursorPage;
import com.smartseason.inventory.platform.PageResponse;
import com.smartseason.inventory.platform.ResourceNotFoundException;
import com.smartseason.inventory.platform.TenantContext;
import com.smartseason.inventory.repo.InputIssueRepository;
import com.smartseason.inventory.web.dto.InputIssueCreateRequest;
import com.smartseason.inventory.web.dto.InputIssueResponse;
import com.smartseason.inventory.web.dto.InputIssueUpdateRequest;
import com.smartseason.inventory.platform.ListFilter;
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
public class InputIssueService {

    private static final String RESOURCE = "InputIssue";
    private static final String ENTITY = "input_issues";

    private static final Map<String, Class<?>> FILTERABLE = Map.ofEntries(
            Map.entry("farmId", UUID.class),
            Map.entry("plotId", UUID.class),
            Map.entry("seasonId", UUID.class),
            Map.entry("inputCode", String.class),
            Map.entry("inputName", String.class),
            Map.entry("unit", String.class),
            Map.entry("issuedTo", UUID.class),
            Map.entry("issuedBy", UUID.class),
            Map.entry("status", InputIssue.Status.class));

    private static final List<String> SEARCHABLE = List.of("inputCode", "inputName", "unit");

    private final InputIssueRepository repository;
    private final EventPublisher events;
    private final CountCache counts;

    public InputIssueService(InputIssueRepository repository, EventPublisher events, CountCache counts) {
        this.repository = repository;
        this.events = events;
        this.counts = counts;
    }

    public PageResponse<InputIssueResponse> list(Pageable pageable, Map<String, String> params) {
        if (ListFilter.isEmpty(params)) {
            return list(pageable);
        }
        UUID tenantId = TenantContext.requireTenantId();
        var spec = ListFilter.<InputIssue>of(tenantId, params, FILTERABLE, SEARCHABLE);
        return PageResponse.from(repository.findAll(spec, pageable).map(InputIssueResponse::from));
    }

    public PageResponse<InputIssueResponse> list(Pageable pageable) {
        UUID tenantId = TenantContext.requireTenantId();

        return PageResponse.of(
                repository.findAllByTenantId(tenantId, pageable).map(InputIssueResponse::from),
                counts.total(ENTITY, tenantId, () -> repository.countByTenantId(tenantId)));
    }

    public CursorPage<InputIssueResponse> listByCursor(String cursor, int size) {
        UUID tenantId = TenantContext.requireTenantId();

        Pageable pageable = PageRequest.of(0, Math.max(1, Math.min(size, 200)));

        Cursor from = Cursor.decode(cursor);
        Slice<InputIssue> slice = (from == null)
                ? repository.findAllByTenantIdOrderByCreatedAtDescIdDesc(tenantId, pageable)
                : repository.findAfterCursor(tenantId, from.createdAt(), from.id(), pageable);

        return CursorPage.of(
                slice,
                slice.getContent().stream().map(InputIssueResponse::from).toList(),
                e -> new Cursor(e.getCreatedAt(), e.getId()));
    }

    public InputIssueResponse get(UUID id) {
        return InputIssueResponse.from(require(id));
    }

    public long count() {
        return repository.countByTenantId(TenantContext.requireTenantId());
    }

    @Transactional
    public InputIssueResponse create(InputIssueCreateRequest request) {
        InputIssue entity = new InputIssue();
        entity.setTenantId(TenantContext.requireTenantId());
        entity.setFarmId(request.farmId());
        entity.setPlotId(request.plotId());
        entity.setSeasonId(request.seasonId());
        entity.setInputCode(request.inputCode());
        entity.setInputName(request.inputName());
        entity.setQuantity(request.quantity());
        entity.setUnit(request.unit());
        entity.setIssuedTo(request.issuedTo());
        entity.setIssuedBy(request.issuedBy());
        entity.setIssuedAt(request.issuedAt());
        entity.setUnitCost(request.unitCost());
        entity.setExpectedRatePerHa(request.expectedRatePerHa());
        entity.setStatus(request.status());

        InputIssue saved = repository.save(entity);
        counts.invalidate(ENTITY, saved.getTenantId());
        events.publish("market", "InputIssueCreated", saved.getId(), InputIssueResponse.from(saved));
        return InputIssueResponse.from(saved);
    }

    @Transactional
    public InputIssueResponse update(UUID id, InputIssueUpdateRequest request) {
        InputIssue entity = require(id);
        if (request.farmId() != null) {
            entity.setFarmId(request.farmId());
        }
        if (request.plotId() != null) {
            entity.setPlotId(request.plotId());
        }
        if (request.seasonId() != null) {
            entity.setSeasonId(request.seasonId());
        }
        if (request.inputCode() != null) {
            entity.setInputCode(request.inputCode());
        }
        if (request.inputName() != null) {
            entity.setInputName(request.inputName());
        }
        if (request.quantity() != null) {
            entity.setQuantity(request.quantity());
        }
        if (request.unit() != null) {
            entity.setUnit(request.unit());
        }
        if (request.issuedTo() != null) {
            entity.setIssuedTo(request.issuedTo());
        }
        if (request.issuedBy() != null) {
            entity.setIssuedBy(request.issuedBy());
        }
        if (request.issuedAt() != null) {
            entity.setIssuedAt(request.issuedAt());
        }
        if (request.unitCost() != null) {
            entity.setUnitCost(request.unitCost());
        }
        if (request.expectedRatePerHa() != null) {
            entity.setExpectedRatePerHa(request.expectedRatePerHa());
        }
        if (request.status() != null) {
            entity.setStatus(request.status());
        }

        InputIssue saved = repository.save(entity);
        events.publish("market", "InputIssueUpdated", saved.getId(), InputIssueResponse.from(saved));
        return InputIssueResponse.from(saved);
    }

    @Transactional
    public void delete(UUID id) {
        InputIssue entity = require(id);
        repository.delete(entity);
        counts.invalidate(ENTITY, entity.getTenantId());
        events.publish("market", "InputIssueDeleted", id, null);
    }

    private InputIssue require(UUID id) {
        return repository.findByIdAndTenantId(id, TenantContext.requireTenantId())
                .orElseThrow(() -> new ResourceNotFoundException(RESOURCE, id));
    }
}
