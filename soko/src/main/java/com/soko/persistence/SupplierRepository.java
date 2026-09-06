package com.soko.persistence;

import com.soko.domain.Supplier;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SupplierRepository extends JpaRepository<Supplier, UUID> {
    List<Supplier> findByTenantIdOrderByNameAsc(UUID tenantId);
    Optional<Supplier> findByIdAndTenantId(UUID id, UUID tenantId);
    List<Supplier> findByIdIn(Collection<UUID> ids);
    long countByTenantId(UUID tenantId);
}
