package com.soko.persistence;

import com.soko.domain.Product;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductRepository extends JpaRepository<Product, UUID> {
    List<Product> findByTenantIdOrderByNameAsc(UUID tenantId);
    Optional<Product> findByIdAndTenantId(UUID id, UUID tenantId);
    Optional<Product> findBySkuAndTenantId(String sku, UUID tenantId);
}
