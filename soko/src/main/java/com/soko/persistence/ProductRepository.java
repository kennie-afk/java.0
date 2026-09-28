package com.soko.persistence;

import com.soko.domain.Product;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductRepository extends JpaRepository<Product, UUID> {
    List<Product> findByTenantIdOrderByNameAsc(UUID tenantId, Pageable pageable);
    List<Product> findByIdIn(Collection<UUID> ids);
    Optional<Product> findByIdAndTenantId(UUID id, UUID tenantId);
    Optional<Product> findBySkuAndTenantId(String sku, UUID tenantId);
}
