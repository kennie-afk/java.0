package com.smartseason.traceability.repo;

import com.smartseason.traceability.domain.TraceBatch;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface TraceBatchRepository extends JpaRepository<TraceBatch, UUID> {

    Optional<TraceBatch> findByIdAndTenantId(UUID id, UUID tenantId);

    Page<TraceBatch> findAllByTenantId(UUID tenantId, Pageable pageable);

    boolean existsByIdAndTenantId(UUID id, UUID tenantId);

    long countByTenantId(UUID tenantId);

    void deleteByIdAndTenantId(UUID id, UUID tenantId);

    Optional<TraceBatch> findByBatchCodeAndTenantId(String batchCode, UUID tenantId);
    Page<TraceBatch> findAllByCommodityCodeAndTenantId(String commodityCode, UUID tenantId, Pageable pageable);
    Page<TraceBatch> findAllByFarmIdAndTenantId(UUID farmId, UUID tenantId, Pageable pageable);
    Page<TraceBatch> findAllByPlotIdAndTenantId(UUID plotId, UUID tenantId, Pageable pageable);
    Page<TraceBatch> findAllBySeasonIdAndTenantId(UUID seasonId, UUID tenantId, Pageable pageable);
    Page<TraceBatch> findAllByCurrentHolderOrgIdAndTenantId(UUID currentHolderOrgId, UUID tenantId, Pageable pageable);
}
