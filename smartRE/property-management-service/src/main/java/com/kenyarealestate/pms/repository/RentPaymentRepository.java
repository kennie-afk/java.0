package com.kenyarealestate.pms.repository;

import com.kenyarealestate.pms.entity.RentPayment;
import com.kenyarealestate.pms.entity.RentPaymentStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RentPaymentRepository extends JpaRepository<RentPayment, UUID> {
    Optional<RentPayment> findByPaymentId(UUID paymentId);
    List<RentPayment> findByInvoiceIdOrderByCreatedAtDesc(UUID invoiceId);
    List<RentPayment> findByInvoiceIdAndStatus(UUID invoiceId, RentPaymentStatus status);
}
