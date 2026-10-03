package com.smartseason.catalog.service;

import com.smartseason.catalog.domain.ProductVariant;
import com.smartseason.catalog.platform.CountCache;
import com.smartseason.catalog.platform.CountCache;
import com.smartseason.catalog.platform.EventPublisher;
import com.smartseason.catalog.platform.ReferenceChecker;
import com.smartseason.catalog.platform.Cursor;
import com.smartseason.catalog.platform.CursorPage;
import com.smartseason.catalog.platform.PageResponse;
import com.smartseason.catalog.platform.ResourceNotFoundException;
import com.smartseason.catalog.platform.TenantContext;
import com.smartseason.catalog.repo.ProductVariantRepository;
import com.smartseason.catalog.web.dto.ProductVariantCreateRequest;
import com.smartseason.catalog.web.dto.ProductVariantResponse;
import com.smartseason.catalog.web.dto.ProductVariantUpdateRequest;
import com.smartseason.catalog.platform.ListFilter;
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
public class ProductVariantService {

    private static final String RESOURCE = "ProductVariant";
    private static final String ENTITY = "product_variants";

    private static final Map<String, Class<?>> FILTERABLE = Map.ofEntries(
            Map.entry("productId", UUID.class),
            Map.entry("sku", String.class),
            Map.entry("variantName", String.class),
            Map.entry("packUnit", String.class),
            Map.entry("grade", String.class),
            Map.entry("active", Boolean.class));

    private static final List<String> SEARCHABLE = List.of("sku", "variantName", "packUnit", "grade");

    private final ProductVariantRepository repository;
    private final EventPublisher events;
    private final CountCache counts;
    private final ReferenceChecker references;

    public ProductVariantService(ProductVariantRepository repository, EventPublisher events, CountCache counts,
            ReferenceChecker references) {
        this.repository = repository;
        this.events = events;
        this.counts = counts;
        this.references = references;
    }

    public PageResponse<ProductVariantResponse> list(Pageable pageable, Map<String, String> params) {
        if (ListFilter.isEmpty(params)) {
            return list(pageable);
        }
        UUID tenantId = TenantContext.requireTenantId();
        var spec = ListFilter.<ProductVariant>of(tenantId, params, FILTERABLE, SEARCHABLE);
        return PageResponse.from(repository.findAll(spec, pageable).map(ProductVariantResponse::from));
    }

    public PageResponse<ProductVariantResponse> list(Pageable pageable) {
        UUID tenantId = TenantContext.requireTenantId();

        return PageResponse.of(
                repository.findAllByTenantId(tenantId, pageable).map(ProductVariantResponse::from),
                counts.total(ENTITY, tenantId, () -> repository.countByTenantId(tenantId)));
    }

    public CursorPage<ProductVariantResponse> listByCursor(String cursor, int size) {
        UUID tenantId = TenantContext.requireTenantId();

        Pageable pageable = PageRequest.of(0, Math.max(1, Math.min(size, 200)));

        Cursor from = Cursor.decode(cursor);
        Slice<ProductVariant> slice = (from == null)
                ? repository.findAllByTenantIdOrderByCreatedAtDescIdDesc(tenantId, pageable)
                : repository.findAfterCursor(tenantId, from.createdAt(), from.id(), pageable);

        return CursorPage.of(
                slice,
                slice.getContent().stream().map(ProductVariantResponse::from).toList(),
                e -> new Cursor(e.getCreatedAt(), e.getId()));
    }

    public ProductVariantResponse get(UUID id) {
        return ProductVariantResponse.from(require(id));
    }

    public long count() {
        return repository.countByTenantId(TenantContext.requireTenantId());
    }

    @Transactional
    public ProductVariantResponse create(ProductVariantCreateRequest request) {
        ProductVariant entity = new ProductVariant();
        entity.setTenantId(TenantContext.requireTenantId());

        references.require("Product", "productId", request.productId());
        entity.setProductId(request.productId());
        entity.setSku(request.sku());
        entity.setVariantName(request.variantName());
        entity.setPackSize(request.packSize());
        entity.setPackUnit(request.packUnit());
        entity.setGrade(request.grade());
        entity.setActive(request.active());

        ProductVariant saved = repository.save(entity);
        counts.invalidate(ENTITY, saved.getTenantId());
        events.publish("market", "ProductVariantCreated", saved.getId(), ProductVariantResponse.from(saved));
        return ProductVariantResponse.from(saved);
    }

    @Transactional
    public ProductVariantResponse update(UUID id, ProductVariantUpdateRequest request) {
        ProductVariant entity = require(id);
        references.require("Product", "productId", request.productId());
        if (request.productId() != null) {
            entity.setProductId(request.productId());
        }
        if (request.sku() != null) {
            entity.setSku(request.sku());
        }
        if (request.variantName() != null) {
            entity.setVariantName(request.variantName());
        }
        if (request.packSize() != null) {
            entity.setPackSize(request.packSize());
        }
        if (request.packUnit() != null) {
            entity.setPackUnit(request.packUnit());
        }
        if (request.grade() != null) {
            entity.setGrade(request.grade());
        }
        if (request.active() != null) {
            entity.setActive(request.active());
        }

        ProductVariant saved = repository.save(entity);
        events.publish("market", "ProductVariantUpdated", saved.getId(), ProductVariantResponse.from(saved));
        return ProductVariantResponse.from(saved);
    }

    @Transactional
    public void delete(UUID id) {
        ProductVariant entity = require(id);
        repository.delete(entity);
        counts.invalidate(ENTITY, entity.getTenantId());
        events.publish("market", "ProductVariantDeleted", id, null);
    }

    private ProductVariant require(UUID id) {
        return repository.findByIdAndTenantId(id, TenantContext.requireTenantId())
                .orElseThrow(() -> new ResourceNotFoundException(RESOURCE, id));
    }
}
