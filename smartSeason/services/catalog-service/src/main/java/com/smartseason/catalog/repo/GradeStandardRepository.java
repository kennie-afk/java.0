package com.smartseason.catalog.repo;

import com.smartseason.catalog.domain.GradeStandard;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface GradeStandardRepository extends JpaRepository<GradeStandard, UUID> {

    Optional<GradeStandard> findByIdAndTenantId(UUID id, UUID tenantId);

    Page<GradeStandard> findAllByTenantId(UUID tenantId, Pageable pageable);

    boolean existsByIdAndTenantId(UUID id, UUID tenantId);

    long countByTenantId(UUID tenantId);

    void deleteByIdAndTenantId(UUID id, UUID tenantId);

    Page<GradeStandard> findAllByCommodityCodeAndTenantId(String commodityCode, UUID tenantId, Pageable pageable);
}
