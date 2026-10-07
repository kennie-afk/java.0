package com.soko.persistence;

import com.soko.domain.Product;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

/**
 * Every query here runs in a transaction, read-only unless a method says otherwise. That is not
 * only about Spring's defaults: the tenant is handed to the database when a transaction begins
 * (see TenantAwareDataSource), and a declared query method outside any transaction would run with
 * no tenant at all and, under row-level security, silently return nothing.
 */
@Transactional(readOnly = true)
public interface ProductRepository extends JpaRepository<Product, UUID> {
    List<Product> findByTenantIdOrderByNameAsc(UUID tenantId, Pageable pageable);
    /** {@code pattern} is a LIKE pattern from {@code Paging.like}; name, SKU and category match. */
    @Query("select p from Product p where p.tenantId = :tenantId and (lower(p.name) like :pattern escape '\\' "
            + "or lower(p.sku) like :pattern escape '\\' or lower(p.category) like :pattern escape '\\') "
            + "order by p.name, p.id")
    Page<Product> search(@Param("tenantId") UUID tenantId, @Param("pattern") String pattern, Pageable pageable);

    List<Product> findByIdIn(Collection<UUID> ids);
    Optional<Product> findByIdAndTenantId(UUID id, UUID tenantId);
    Optional<Product> findBySkuAndTenantId(String sku, UUID tenantId);
}
