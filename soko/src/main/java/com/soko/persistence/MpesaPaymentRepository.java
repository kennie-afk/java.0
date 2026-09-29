package com.soko.persistence;

import com.soko.domain.MpesaPayment;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MpesaPaymentRepository extends JpaRepository<MpesaPayment, UUID> {

    Optional<MpesaPayment> findByCheckoutRequestId(String checkoutRequestId);

    List<MpesaPayment> findByPurposeAndReferenceIdOrderByInitiatedAtDesc(
            String purpose, UUID referenceId);

    Optional<MpesaPayment> findFirstByPurposeAndReferenceIdAndStatusOrderByInitiatedAtDesc(
            String purpose, UUID referenceId, String status);
}
