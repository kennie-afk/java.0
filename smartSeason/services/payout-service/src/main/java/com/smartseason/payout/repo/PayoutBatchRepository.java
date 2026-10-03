package com.smartseason.payout.repo;

import com.smartseason.payout.domain.PayoutBatch;
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
public interface PayoutBatchRepository extends JpaRepository<PayoutBatch, UUID>, JpaSpecificationExecutor<PayoutBatch> {

    Optional<PayoutBatch> findByIdAndTenantId(UUID id, UUID tenantId);

    Slice<PayoutBatch> findAllByTenantId(UUID tenantId, Pageable pageable);

    boolean existsByIdAndTenantId(UUID id, UUID tenantId);

    long countByTenantId(UUID tenantId);

    void deleteByIdAndTenantId(UUID id, UUID tenantId);

    Slice<PayoutBatch> findAllByTenantIdOrderByCreatedAtDescIdDesc(UUID tenantId, Pageable pageable);

    @Query("SELECT e FROM PayoutBatch e WHERE e.tenantId = :tenantId "
            + "AND (e.createdAt < :cursorAt "
            + "     OR (e.createdAt = :cursorAt AND e.id < :cursorId)) "
            + "ORDER BY e.createdAt DESC, e.id DESC")
    Slice<PayoutBatch> findAfterCursor(@Param("tenantId") UUID tenantId,
                                  @Param("cursorAt") Instant cursorAt,
                                  @Param("cursorId") UUID cursorId,
                                  Pageable pageable);

    Optional<PayoutBatch> findByBatchNumberAndTenantId(String batchNumber, UUID tenantId);
    Page<PayoutBatch> findAllByFarmIdAndTenantId(UUID farmId, UUID tenantId, Pageable pageable);
}
