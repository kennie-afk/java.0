package com.smartseason.catalog.repo;

import com.smartseason.catalog.domain.Commodity;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CommodityRepository extends JpaRepository<Commodity, UUID> {

    Optional<Commodity> findByIdAndTenantId(UUID id, UUID tenantId);

    Slice<Commodity> findAllByTenantId(UUID tenantId, Pageable pageable);

    boolean existsByIdAndTenantId(UUID id, UUID tenantId);

    long countByTenantId(UUID tenantId);

    void deleteByIdAndTenantId(UUID id, UUID tenantId);

    Optional<Commodity> findByCodeAndTenantId(String code, UUID tenantId);
    Page<Commodity> findAllByCategoryAndTenantId(String category, UUID tenantId, Pageable pageable);
}
