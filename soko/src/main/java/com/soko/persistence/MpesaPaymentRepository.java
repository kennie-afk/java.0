package com.soko.persistence;

import com.soko.domain.MpesaPayment;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

public interface MpesaPaymentRepository extends JpaRepository<MpesaPayment, UUID> {

    /**
     * Row-locked: a callback and its retry can arrive together, and both would otherwise read
     * PENDING and both settle the order. The second waits here, then sees the first one's result.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<MpesaPayment> findByCheckoutRequestId(String checkoutRequestId);

    List<MpesaPayment> findByPurposeAndReferenceIdOrderByInitiatedAtDesc(
            String purpose, UUID referenceId);

    Optional<MpesaPayment> findFirstByPurposeAndReferenceIdAndStatusOrderByInitiatedAtDesc(
            String purpose, UUID referenceId, String status);
}
