package com.kenyarealestate.pms.repository;

import com.kenyarealestate.pms.entity.InvoiceStatus;
import com.kenyarealestate.pms.entity.RentInvoice;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RentInvoiceRepository extends JpaRepository<RentInvoice, UUID> {

    Optional<RentInvoice> findByLeaseIdAndPeriodStart(UUID leaseId, LocalDate periodStart);
    Optional<RentInvoice> findByInvoiceNumber(String invoiceNumber);
    List<RentInvoice> findByLeaseIdOrderByPeriodStartDesc(UUID leaseId);
    Page<RentInvoice> findByLandlordIdOrderByDueDateDesc(UUID landlordId, Pageable pageable);
    Page<RentInvoice> findByLandlordIdAndStatusOrderByDueDateDesc(UUID landlordId, InvoiceStatus status, Pageable pageable);
    Page<RentInvoice> findByTenantIdInOrderByDueDateDesc(List<UUID> tenantIds, Pageable pageable);

    @Query("""
           SELECT i FROM RentInvoice i
           WHERE i.status IN (com.kenyarealestate.pms.entity.InvoiceStatus.PENDING,
                              com.kenyarealestate.pms.entity.InvoiceStatus.PARTIAL,
                              com.kenyarealestate.pms.entity.InvoiceStatus.OVERDUE)
             AND i.dueDate < :today
           ORDER BY i.dueDate ASC
           """)
    List<RentInvoice> findChaseable(@Param("today") LocalDate today, Pageable pageable);

    @Query("""
           SELECT COALESCE(SUM(i.amountDue - i.amountPaid), 0) FROM RentInvoice i
           WHERE i.landlordId = :landlordId
             AND i.status IN (com.kenyarealestate.pms.entity.InvoiceStatus.PENDING,
                              com.kenyarealestate.pms.entity.InvoiceStatus.PARTIAL,
                              com.kenyarealestate.pms.entity.InvoiceStatus.OVERDUE)
           """)
    java.math.BigDecimal outstandingFor(@Param("landlordId") UUID landlordId);

    long countByLandlordIdAndStatus(UUID landlordId, InvoiceStatus status);
}
