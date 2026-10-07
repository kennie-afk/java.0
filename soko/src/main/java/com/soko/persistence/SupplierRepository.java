package com.soko.persistence;

import com.soko.domain.Supplier;
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
public interface SupplierRepository extends JpaRepository<Supplier, UUID> {
    List<Supplier> findByTenantIdOrderByNameAsc(UUID tenantId, Pageable pageable);
    @Query("select s from Supplier s where s.tenantId = :tenantId and (lower(s.name) like :pattern escape '\\' "
            + "or lower(s.county) like :pattern escape '\\') order by s.name, s.id")
    Page<Supplier> search(@Param("tenantId") UUID tenantId, @Param("pattern") String pattern, Pageable pageable);

    Optional<Supplier> findByIdAndTenantId(UUID id, UUID tenantId);
    List<Supplier> findByIdIn(Collection<UUID> ids);
    long countByTenantId(UUID tenantId);
}
