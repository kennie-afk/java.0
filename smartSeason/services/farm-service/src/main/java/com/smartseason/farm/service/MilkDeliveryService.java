package com.smartseason.farm.service;

import com.smartseason.farm.domain.MilkDelivery;
import com.smartseason.farm.platform.CountCache;
import com.smartseason.farm.platform.CountCache;
import com.smartseason.farm.platform.EventPublisher;
import com.smartseason.farm.platform.ReferenceChecker;
import com.smartseason.farm.platform.Cursor;
import com.smartseason.farm.platform.CursorPage;
import com.smartseason.farm.platform.PageResponse;
import com.smartseason.farm.platform.ResourceNotFoundException;
import com.smartseason.farm.platform.TenantContext;
import com.smartseason.farm.repo.MilkDeliveryRepository;
import com.smartseason.farm.web.dto.MilkDeliveryCreateRequest;
import com.smartseason.farm.web.dto.MilkDeliveryResponse;
import com.smartseason.farm.web.dto.MilkDeliveryUpdateRequest;
import com.smartseason.farm.platform.ListFilter;
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
public class MilkDeliveryService {

    private static final String RESOURCE = "MilkDelivery";
    private static final String ENTITY = "milk_deliveries";

    private static final Map<String, Class<?>> FILTERABLE = Map.ofEntries(
            Map.entry("farmId", UUID.class),
            Map.entry("buyerName", String.class),
            Map.entry("receiptNo", String.class),
            Map.entry("alcoholTestPassed", Boolean.class),
            Map.entry("currency", String.class),
            Map.entry("status", MilkDelivery.Status.class));

    private static final List<String> SEARCHABLE = List.of("buyerName", "receiptNo", "currency");

    private final MilkDeliveryRepository repository;
    private final EventPublisher events;
    private final CountCache counts;
    private final ReferenceChecker references;

    public MilkDeliveryService(MilkDeliveryRepository repository, EventPublisher events, CountCache counts,
            ReferenceChecker references) {
        this.repository = repository;
        this.events = events;
        this.counts = counts;
        this.references = references;
    }

    public PageResponse<MilkDeliveryResponse> list(Pageable pageable, Map<String, String> params) {
        if (ListFilter.isEmpty(params)) {
            return list(pageable);
        }
        UUID tenantId = TenantContext.requireTenantId();
        var spec = ListFilter.<MilkDelivery>of(tenantId, params, FILTERABLE, SEARCHABLE);
        return PageResponse.from(repository.findAll(spec, pageable).map(MilkDeliveryResponse::from));
    }

    public PageResponse<MilkDeliveryResponse> list(Pageable pageable) {
        UUID tenantId = TenantContext.requireTenantId();

        return PageResponse.of(
                repository.findAllByTenantId(tenantId, pageable).map(MilkDeliveryResponse::from),
                counts.total(ENTITY, tenantId, () -> repository.countByTenantId(tenantId)));
    }

    public CursorPage<MilkDeliveryResponse> listByCursor(String cursor, int size) {
        UUID tenantId = TenantContext.requireTenantId();

        Pageable pageable = PageRequest.of(0, Math.max(1, Math.min(size, 200)));

        Cursor from = Cursor.decode(cursor);
        Slice<MilkDelivery> slice = (from == null)
                ? repository.findAllByTenantIdOrderByCreatedAtDescIdDesc(tenantId, pageable)
                : repository.findAfterCursor(tenantId, from.createdAt(), from.id(), pageable);

        return CursorPage.of(
                slice,
                slice.getContent().stream().map(MilkDeliveryResponse::from).toList(),
                e -> new Cursor(e.getCreatedAt(), e.getId()));
    }

    public MilkDeliveryResponse get(UUID id) {
        return MilkDeliveryResponse.from(require(id));
    }

    public long count() {
        return repository.countByTenantId(TenantContext.requireTenantId());
    }

    @Transactional
    public MilkDeliveryResponse create(MilkDeliveryCreateRequest request) {
        MilkDelivery entity = new MilkDelivery();
        entity.setTenantId(TenantContext.requireTenantId());

        references.require("Farm", "farmId", request.farmId());
        entity.setFarmId(request.farmId());
        entity.setDeliveredOn(request.deliveredOn());
        entity.setBuyerName(request.buyerName());
        entity.setReceiptNo(request.receiptNo());
        entity.setLitresDelivered(request.litresDelivered());
        entity.setLitresRejected(request.litresRejected());
        entity.setFatPct(request.fatPct());
        entity.setSnfPct(request.snfPct());
        entity.setTemperatureC(request.temperatureC());
        entity.setAlcoholTestPassed(request.alcoholTestPassed());
        entity.setPricePerLitre(request.pricePerLitre());
        entity.setCurrency(request.currency());
        entity.setStatus(request.status());
        entity.setNotes(request.notes());

        MilkDelivery saved = repository.save(entity);
        counts.invalidate(ENTITY, saved.getTenantId());
        events.publish("farm", "MilkDeliveryCreated", saved.getId(), MilkDeliveryResponse.from(saved));
        return MilkDeliveryResponse.from(saved);
    }

    @Transactional
    public MilkDeliveryResponse update(UUID id, MilkDeliveryUpdateRequest request) {
        MilkDelivery entity = require(id);
        references.require("Farm", "farmId", request.farmId());
        if (request.farmId() != null) {
            entity.setFarmId(request.farmId());
        }
        if (request.deliveredOn() != null) {
            entity.setDeliveredOn(request.deliveredOn());
        }
        if (request.buyerName() != null) {
            entity.setBuyerName(request.buyerName());
        }
        if (request.receiptNo() != null) {
            entity.setReceiptNo(request.receiptNo());
        }
        if (request.litresDelivered() != null) {
            entity.setLitresDelivered(request.litresDelivered());
        }
        if (request.litresRejected() != null) {
            entity.setLitresRejected(request.litresRejected());
        }
        if (request.fatPct() != null) {
            entity.setFatPct(request.fatPct());
        }
        if (request.snfPct() != null) {
            entity.setSnfPct(request.snfPct());
        }
        if (request.temperatureC() != null) {
            entity.setTemperatureC(request.temperatureC());
        }
        if (request.alcoholTestPassed() != null) {
            entity.setAlcoholTestPassed(request.alcoholTestPassed());
        }
        if (request.pricePerLitre() != null) {
            entity.setPricePerLitre(request.pricePerLitre());
        }
        if (request.currency() != null) {
            entity.setCurrency(request.currency());
        }
        if (request.status() != null) {
            entity.setStatus(request.status());
        }
        if (request.notes() != null) {
            entity.setNotes(request.notes());
        }

        MilkDelivery saved = repository.save(entity);
        events.publish("farm", "MilkDeliveryUpdated", saved.getId(), MilkDeliveryResponse.from(saved));
        return MilkDeliveryResponse.from(saved);
    }

    @Transactional
    public void delete(UUID id) {
        MilkDelivery entity = require(id);
        repository.delete(entity);
        counts.invalidate(ENTITY, entity.getTenantId());
        events.publish("farm", "MilkDeliveryDeleted", id, null);
    }

    private MilkDelivery require(UUID id) {
        return repository.findByIdAndTenantId(id, TenantContext.requireTenantId())
                .orElseThrow(() -> new ResourceNotFoundException(RESOURCE, id));
    }
}
