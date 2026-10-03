package com.smartseason.payout.repo;

import com.smartseason.payout.domain.PayoutItem;
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
public interface PayoutItemRepository extends JpaRepository<PayoutItem, UUID>, JpaSpecificationExecutor<PayoutItem> {

    Optional<PayoutItem> findByIdAndTenantId(UUID id, UUID tenantId);

    Slice<PayoutItem> findAllByTenantId(UUID tenantId, Pageable pageable);

    boolean existsByIdAndTenantId(UUID id, UUID tenantId);

    long countByTenantId(UUID tenantId);

    void deleteByIdAndTenantId(UUID id, UUID tenantId);

    Slice<PayoutItem> findAllByTenantIdOrderByCreatedAtDescIdDesc(UUID tenantId, Pageable pageable);

    @Query("SELECT e FROM PayoutItem e WHERE e.tenantId = :tenantId "
            + "AND (e.createdAt < :cursorAt "
            + "     OR (e.createdAt = :cursorAt AND e.id < :cursorId)) "
            + "ORDER BY e.createdAt DESC, e.id DESC")
    Slice<PayoutItem> findAfterCursor(@Param("tenantId") UUID tenantId,
                                  @Param("cursorAt") Instant cursorAt,
                                  @Param("cursorId") UUID cursorId,
                                  Pageable pageable);

    Page<PayoutItem> findAllByBatchIdAndTenantId(UUID batchId, UUID tenantId, Pageable pageable);
    Page<PayoutItem> findAllBySettlementIdAndTenantId(UUID settlementId, UUID tenantId, Pageable pageable);
    Page<PayoutItem> findAllByPayeeIdAndTenantId(UUID payeeId, UUID tenantId, Pageable pageable);
    Page<PayoutItem> findAllByPaymentIntentIdAndTenantId(UUID paymentIntentId, UUID tenantId, Pageable pageable);
    Optional<PayoutItem> findByIdempotencyKeyAndTenantId(String idempotencyKey, UUID tenantId);
}
