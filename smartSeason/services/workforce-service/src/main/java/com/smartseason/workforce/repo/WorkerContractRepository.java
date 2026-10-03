package com.smartseason.workforce.repo;

import com.smartseason.workforce.domain.WorkerContract;
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
public interface WorkerContractRepository extends JpaRepository<WorkerContract, UUID>, JpaSpecificationExecutor<WorkerContract> {

    Optional<WorkerContract> findByIdAndTenantId(UUID id, UUID tenantId);

    Slice<WorkerContract> findAllByTenantId(UUID tenantId, Pageable pageable);

    boolean existsByIdAndTenantId(UUID id, UUID tenantId);

    long countByTenantId(UUID tenantId);

    void deleteByIdAndTenantId(UUID id, UUID tenantId);

    Slice<WorkerContract> findAllByTenantIdOrderByCreatedAtDescIdDesc(UUID tenantId, Pageable pageable);

    @Query("SELECT e FROM WorkerContract e WHERE e.tenantId = :tenantId "
            + "AND (e.createdAt < :cursorAt "
            + "     OR (e.createdAt = :cursorAt AND e.id < :cursorId)) "
            + "ORDER BY e.createdAt DESC, e.id DESC")
    Slice<WorkerContract> findAfterCursor(@Param("tenantId") UUID tenantId,
                                  @Param("cursorAt") Instant cursorAt,
                                  @Param("cursorId") UUID cursorId,
                                  Pageable pageable);

    Page<WorkerContract> findAllByWorkerIdAndTenantId(UUID workerId, UUID tenantId, Pageable pageable);
    Page<WorkerContract> findAllByFarmIdAndTenantId(UUID farmId, UUID tenantId, Pageable pageable);
}
