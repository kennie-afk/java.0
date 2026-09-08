package com.smartseason.catalog.repo;

import com.smartseason.catalog.domain.ProductVariant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ProductVariantRepository extends JpaRepository<ProductVariant, UUID> {

    Optional<ProductVariant> findByIdAndTenantId(UUID id, UUID tenantId);

    Slice<ProductVariant> findAllByTenantId(UUID tenantId, Pageable pageable);

    boolean existsByIdAndTenantId(UUID id, UUID tenantId);

    long countByTenantId(UUID tenantId);

    void deleteByIdAndTenantId(UUID id, UUID tenantId);

    Page<ProductVariant> findAllByProductIdAndTenantId(UUID productId, UUID tenantId, Pageable pageable);
    Optional<ProductVariant> findBySkuAndTenantId(String sku, UUID tenantId);
}
