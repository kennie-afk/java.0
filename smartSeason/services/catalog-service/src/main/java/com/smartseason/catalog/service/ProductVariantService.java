package com.smartseason.catalog.service;

import com.smartseason.catalog.domain.ProductVariant;
import com.smartseason.catalog.platform.CountCache;
import com.smartseason.catalog.platform.CountCache;
import com.smartseason.catalog.platform.EventPublisher;
import com.smartseason.catalog.platform.PageResponse;
import com.smartseason.catalog.platform.ResourceNotFoundException;
import com.smartseason.catalog.platform.TenantContext;
import com.smartseason.catalog.repo.ProductVariantRepository;
import com.smartseason.catalog.web.dto.ProductVariantCreateRequest;
import com.smartseason.catalog.web.dto.ProductVariantResponse;
import com.smartseason.catalog.web.dto.ProductVariantUpdateRequest;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class ProductVariantService {

    private static final String RESOURCE = "ProductVariant";
    private static final String ENTITY = "product_variants";

    private final ProductVariantRepository repository;
    private final EventPublisher events;
    private final CountCache counts;

    public ProductVariantService(ProductVariantRepository repository, EventPublisher events, CountCache counts) {
        this.repository = repository;
        this.events = events;
        this.counts = counts;
    }

    public PageResponse<ProductVariantResponse> list(Pageable pageable) {
        UUID tenantId = TenantContext.requireTenantId();

        return PageResponse.of(
                repository.findAllByTenantId(tenantId, pageable).map(ProductVariantResponse::from),
                counts.total(ENTITY, tenantId, () -> repository.countByTenantId(tenantId)));
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
