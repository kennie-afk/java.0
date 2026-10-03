package com.smartseason.traceability.repo;

import com.smartseason.traceability.domain.TraceBatch;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface TraceBatchRepository extends JpaRepository<TraceBatch, UUID>, JpaSpecificationExecutor<TraceBatch> {

    Optional<TraceBatch> findByIdAndTenantId(UUID id, UUID tenantId);

    Slice<TraceBatch> findAllByTenantId(UUID tenantId, Pageable pageable);

    boolean existsByIdAndTenantId(UUID id, UUID tenantId);

    long countByTenantId(UUID tenantId);

    void deleteByIdAndTenantId(UUID id, UUID tenantId);

    Slice<TraceBatch> findAllByTenantIdOrderByCreatedAtDescIdDesc(UUID tenantId, Pageable pageable);

    @Query("SELECT e FROM TraceBatch e WHERE e.tenantId = :tenantId "
            + "AND (e.createdAt < :cursorAt "
            + "     OR (e.createdAt = :cursorAt AND e.id < :cursorId)) "
            + "ORDER BY e.createdAt DESC, e.id DESC")
    Slice<TraceBatch> findAfterCursor(@Param("tenantId") UUID tenantId,
                                  @Param("cursorAt") Instant cursorAt,
                                  @Param("cursorId") UUID cursorId,
                                  Pageable pageable);

    Optional<TraceBatch> findByBatchCodeAndTenantId(String batchCode, UUID tenantId);
    Page<TraceBatch> findAllByCommodityCodeAndTenantId(String commodityCode, UUID tenantId, Pageable pageable);
    Page<TraceBatch> findAllByFarmIdAndTenantId(UUID farmId, UUID tenantId, Pageable pageable);
    Page<TraceBatch> findAllByPlotIdAndTenantId(UUID plotId, UUID tenantId, Pageable pageable);
    Page<TraceBatch> findAllBySeasonIdAndTenantId(UUID seasonId, UUID tenantId, Pageable pageable);
    Page<TraceBatch> findAllByCurrentHolderOrgIdAndTenantId(UUID currentHolderOrgId, UUID tenantId, Pageable pageable);
}
