package com.kenyarealestate.pms.repository;

import com.kenyarealestate.pms.entity.RentPayment;
import com.kenyarealestate.pms.entity.RentPaymentStatus;
import com.kenyarealestate.pms.service.MriLine;
import java.time.LocalDateTime;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RentPaymentRepository extends JpaRepository<RentPayment, UUID> {
    Optional<RentPayment> findByPaymentId(UUID paymentId);
    List<RentPayment> findByInvoiceIdOrderByCreatedAtDesc(UUID invoiceId);
    List<RentPayment> findByInvoiceIdAndStatus(UUID invoiceId, RentPaymentStatus status);

    /** Confirmed rent receipts for one landlord whose paid-at falls in [from, to). Landlord comes from the invoice. */
    @Query("""
            select new com.kenyarealestate.pms.service.MriLine(
                p.id, i.invoiceNumber, u.label, u.propertyId, t.fullName, p.method, p.amount, p.paidAt, p.mpesaReceipt)
            from RentPayment p
            join RentInvoice i on i.id = p.invoiceId
            join Unit u on u.id = i.unitId
            join Tenant t on t.id = i.tenantId
            where i.landlordId = :landlordId
              and p.status = com.kenyarealestate.pms.entity.RentPaymentStatus.CONFIRMED
              and p.paidAt >= :from and p.paidAt < :to
            order by p.paidAt
            """)
    List<MriLine> confirmedRentBetween(@Param("landlordId") UUID landlordId,
                                       @Param("from") LocalDateTime from,
                                       @Param("to") LocalDateTime to);
}
