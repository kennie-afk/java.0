package com.smartseason.payout.repo;

import com.smartseason.payout.domain.PayoutItem;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PayoutItemRepository extends JpaRepository<PayoutItem, UUID> {

    Optional<PayoutItem> findByIdAndTenantId(UUID id, UUID tenantId);

    Slice<PayoutItem> findAllByTenantId(UUID tenantId, Pageable pageable);

    boolean existsByIdAndTenantId(UUID id, UUID tenantId);

    long countByTenantId(UUID tenantId);

    void deleteByIdAndTenantId(UUID id, UUID tenantId);

    Page<PayoutItem> findAllByBatchIdAndTenantId(UUID batchId, UUID tenantId, Pageable pageable);
    Page<PayoutItem> findAllBySettlementIdAndTenantId(UUID settlementId, UUID tenantId, Pageable pageable);
    Page<PayoutItem> findAllByPayeeIdAndTenantId(UUID payeeId, UUID tenantId, Pageable pageable);
    Page<PayoutItem> findAllByPaymentIntentIdAndTenantId(UUID paymentIntentId, UUID tenantId, Pageable pageable);
    Optional<PayoutItem> findByIdempotencyKeyAndTenantId(String idempotencyKey, UUID tenantId);
}
