package com.smartseason.fraud.service;

import com.smartseason.fraud.domain.FraudRule;
import com.smartseason.fraud.platform.CountCache;
import com.smartseason.fraud.platform.CountCache;
import com.smartseason.fraud.platform.EventPublisher;
import com.smartseason.fraud.platform.Cursor;
import com.smartseason.fraud.platform.CursorPage;
import com.smartseason.fraud.platform.PageResponse;
import com.smartseason.fraud.platform.ResourceNotFoundException;
import com.smartseason.fraud.platform.TenantContext;
import com.smartseason.fraud.repo.FraudRuleRepository;
import com.smartseason.fraud.web.dto.FraudRuleCreateRequest;
import com.smartseason.fraud.web.dto.FraudRuleResponse;
import com.smartseason.fraud.web.dto.FraudRuleUpdateRequest;
import com.smartseason.fraud.platform.ListFilter;
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
public class FraudRuleService {

    private static final String RESOURCE = "FraudRule";
    private static final String ENTITY = "fraud_rules";

    private static final Map<String, Class<?>> FILTERABLE = Map.ofEntries(
            Map.entry("code", String.class),
            Map.entry("typology", FraudRule.Typology.class),
            Map.entry("name", String.class),
            Map.entry("severity", FraudRule.Severity.class),
            Map.entry("enabled", Boolean.class),
            Map.entry("autoHoldPayout", Boolean.class));

    private static final List<String> SEARCHABLE = List.of("code", "name");

    private final FraudRuleRepository repository;
    private final EventPublisher events;
    private final CountCache counts;

    public FraudRuleService(FraudRuleRepository repository, EventPublisher events, CountCache counts) {
        this.repository = repository;
        this.events = events;
        this.counts = counts;
    }

    public PageResponse<FraudRuleResponse> list(Pageable pageable, Map<String, String> params) {
        if (ListFilter.isEmpty(params)) {
            return list(pageable);
        }
        UUID tenantId = TenantContext.requireTenantId();
        var spec = ListFilter.<FraudRule>of(tenantId, params, FILTERABLE, SEARCHABLE);
        return PageResponse.from(repository.findAll(spec, pageable).map(FraudRuleResponse::from));
    }

    public PageResponse<FraudRuleResponse> list(Pageable pageable) {
        UUID tenantId = TenantContext.requireTenantId();

        return PageResponse.of(
                repository.findAllByTenantId(tenantId, pageable).map(FraudRuleResponse::from),
                counts.total(ENTITY, tenantId, () -> repository.countByTenantId(tenantId)));
    }

    public CursorPage<FraudRuleResponse> listByCursor(String cursor, int size) {
        UUID tenantId = TenantContext.requireTenantId();

        Pageable pageable = PageRequest.of(0, Math.max(1, Math.min(size, 200)));

        Cursor from = Cursor.decode(cursor);
        Slice<FraudRule> slice = (from == null)
                ? repository.findAllByTenantIdOrderByCreatedAtDescIdDesc(tenantId, pageable)
                : repository.findAfterCursor(tenantId, from.createdAt(), from.id(), pageable);

        return CursorPage.of(
                slice,
                slice.getContent().stream().map(FraudRuleResponse::from).toList(),
                e -> new Cursor(e.getCreatedAt(), e.getId()));
    }

    public FraudRuleResponse get(UUID id) {
        return FraudRuleResponse.from(require(id));
    }

    public long count() {
        return repository.countByTenantId(TenantContext.requireTenantId());
    }

    @Transactional
    public FraudRuleResponse create(FraudRuleCreateRequest request) {
        FraudRule entity = new FraudRule();
        entity.setTenantId(TenantContext.requireTenantId());
        entity.setCode(request.code());
        entity.setTypology(request.typology());
        entity.setName(request.name());
        entity.setDescription(request.description());
        entity.setExpression(request.expression());
        entity.setThreshold(request.threshold());
        entity.setSeverity(request.severity());
        entity.setWeight(request.weight());
        entity.setEnabled(request.enabled());
        entity.setAutoHoldPayout(request.autoHoldPayout());

        FraudRule saved = repository.save(entity);
        counts.invalidate(ENTITY, saved.getTenantId());
        events.publish("workforce", "FraudRuleCreated", saved.getId(), FraudRuleResponse.from(saved));
        return FraudRuleResponse.from(saved);
    }

    @Transactional
    public FraudRuleResponse update(UUID id, FraudRuleUpdateRequest request) {
        FraudRule entity = require(id);
        if (request.code() != null) {
            entity.setCode(request.code());
        }
        if (request.typology() != null) {
            entity.setTypology(request.typology());
        }
        if (request.name() != null) {
            entity.setName(request.name());
        }
        if (request.description() != null) {
            entity.setDescription(request.description());
        }
        if (request.expression() != null) {
            entity.setExpression(request.expression());
        }
        if (request.threshold() != null) {
            entity.setThreshold(request.threshold());
        }
        if (request.severity() != null) {
            entity.setSeverity(request.severity());
        }
        if (request.weight() != null) {
            entity.setWeight(request.weight());
        }
        if (request.enabled() != null) {
            entity.setEnabled(request.enabled());
        }
        if (request.autoHoldPayout() != null) {
            entity.setAutoHoldPayout(request.autoHoldPayout());
        }

        FraudRule saved = repository.save(entity);
        events.publish("workforce", "FraudRuleUpdated", saved.getId(), FraudRuleResponse.from(saved));
        return FraudRuleResponse.from(saved);
    }

    @Transactional
    public void delete(UUID id) {
        FraudRule entity = require(id);
        repository.delete(entity);
        counts.invalidate(ENTITY, entity.getTenantId());
        events.publish("workforce", "FraudRuleDeleted", id, null);
    }

    private FraudRule require(UUID id) {
        return repository.findByIdAndTenantId(id, TenantContext.requireTenantId())
                .orElseThrow(() -> new ResourceNotFoundException(RESOURCE, id));
    }
}
