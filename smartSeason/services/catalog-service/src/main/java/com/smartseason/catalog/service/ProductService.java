package com.smartseason.catalog.service;

import com.smartseason.catalog.domain.Product;
import com.smartseason.catalog.platform.EventPublisher;
import com.smartseason.catalog.platform.PageResponse;
import com.smartseason.catalog.platform.ResourceNotFoundException;
import com.smartseason.catalog.platform.TenantContext;
import com.smartseason.catalog.repo.ProductRepository;
import com.smartseason.catalog.web.dto.ProductCreateRequest;
import com.smartseason.catalog.web.dto.ProductResponse;
import com.smartseason.catalog.web.dto.ProductUpdateRequest;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class ProductService {

    private static final String RESOURCE = "Product";

    private final ProductRepository repository;
    private final EventPublisher events;

    public ProductService(ProductRepository repository, EventPublisher events) {
        this.repository = repository;
        this.events = events;
    }

    public PageResponse<ProductResponse> list(Pageable pageable) {
        return PageResponse.from(
                repository.findAllByTenantId(TenantContext.requireTenantId(), pageable)
                        .map(ProductResponse::from));
    }

    public ProductResponse get(UUID id) {
        return ProductResponse.from(require(id));
    }

    public long count() {
        return repository.countByTenantId(TenantContext.requireTenantId());
    }

    @Transactional
    public ProductResponse create(ProductCreateRequest request) {
        Product entity = new Product();
        entity.setTenantId(TenantContext.requireTenantId());
        entity.setCommodityCode(request.commodityCode());
        entity.setName(request.name());
        entity.setDescription(request.description());
        entity.setDefaultGrade(request.defaultGrade());
        entity.setStatus(request.status());

        Product saved = repository.save(entity);
        events.publish("market", "ProductCreated", saved.getId(), ProductResponse.from(saved));
        return ProductResponse.from(saved);
    }

    @Transactional
    public ProductResponse update(UUID id, ProductUpdateRequest request) {
        Product entity = require(id);
        if (request.commodityCode() != null) {
            entity.setCommodityCode(request.commodityCode());
        }
        if (request.name() != null) {
            entity.setName(request.name());
        }
        if (request.description() != null) {
            entity.setDescription(request.description());
        }
        if (request.defaultGrade() != null) {
            entity.setDefaultGrade(request.defaultGrade());
        }
        if (request.status() != null) {
            entity.setStatus(request.status());
        }

        Product saved = repository.save(entity);
        events.publish("market", "ProductUpdated", saved.getId(), ProductResponse.from(saved));
        return ProductResponse.from(saved);
    }

    @Transactional
    public void delete(UUID id) {
        Product entity = require(id);
        repository.delete(entity);
        events.publish("market", "ProductDeleted", id, null);
    }

    private Product require(UUID id) {
        return repository.findByIdAndTenantId(id, TenantContext.requireTenantId())
                .orElseThrow(() -> new ResourceNotFoundException(RESOURCE, id));
    }
}
