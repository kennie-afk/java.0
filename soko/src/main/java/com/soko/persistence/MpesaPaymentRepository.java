package com.soko.persistence;

import com.soko.domain.MpesaPayment;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.transaction.annotation.Transactional;

/**
 * Every query here runs in a transaction, read-only unless a method says otherwise. That is not
 * only about Spring's defaults: the tenant is handed to the database when a transaction begins
 * (see TenantAwareDataSource), and a declared query method outside any transaction would run with
 * no tenant at all and, under row-level security, silently return nothing.
 */
@Transactional(readOnly = true)
public interface MpesaPaymentRepository extends JpaRepository<MpesaPayment, UUID> {

    /**
     * Row-locked: a callback and its retry can arrive together, and both would otherwise read
     * PENDING and both settle the order. The second waits here, then sees the first one's result.
     */
    @Transactional
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<MpesaPayment> findByCheckoutRequestId(String checkoutRequestId);

    List<MpesaPayment> findByPurposeAndReferenceIdOrderByInitiatedAtDesc(
            String purpose, UUID referenceId);

    List<MpesaPayment> findByTenantIdAndStatusOrderByInitiatedAtDesc(
            UUID tenantId, String status, org.springframework.data.domain.Pageable pageable);

    Optional<MpesaPayment> findFirstByPurposeAndReferenceIdAndStatusOrderByInitiatedAtDesc(
            String purpose, UUID referenceId, String status);
}
