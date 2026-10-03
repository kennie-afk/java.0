package com.smartseason.inventory.repo;

import com.smartseason.inventory.domain.InputConsumption;
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
public interface InputConsumptionRepository extends JpaRepository<InputConsumption, UUID>, JpaSpecificationExecutor<InputConsumption> {

    Optional<InputConsumption> findByIdAndTenantId(UUID id, UUID tenantId);

    Slice<InputConsumption> findAllByTenantId(UUID tenantId, Pageable pageable);

    boolean existsByIdAndTenantId(UUID id, UUID tenantId);

    long countByTenantId(UUID tenantId);

    void deleteByIdAndTenantId(UUID id, UUID tenantId);

    Slice<InputConsumption> findAllByTenantIdOrderByCreatedAtDescIdDesc(UUID tenantId, Pageable pageable);

    @Query("SELECT e FROM InputConsumption e WHERE e.tenantId = :tenantId "
            + "AND (e.createdAt < :cursorAt "
            + "     OR (e.createdAt = :cursorAt AND e.id < :cursorId)) "
            + "ORDER BY e.createdAt DESC, e.id DESC")
    Slice<InputConsumption> findAfterCursor(@Param("tenantId") UUID tenantId,
                                  @Param("cursorAt") Instant cursorAt,
                                  @Param("cursorId") UUID cursorId,
                                  Pageable pageable);

    Page<InputConsumption> findAllByInputIssueIdAndTenantId(UUID inputIssueId, UUID tenantId, Pageable pageable);
    Page<InputConsumption> findAllByFarmIdAndTenantId(UUID farmId, UUID tenantId, Pageable pageable);
    Page<InputConsumption> findAllByPlotIdAndTenantId(UUID plotId, UUID tenantId, Pageable pageable);
    Page<InputConsumption> findAllBySeasonIdAndTenantId(UUID seasonId, UUID tenantId, Pageable pageable);
}
