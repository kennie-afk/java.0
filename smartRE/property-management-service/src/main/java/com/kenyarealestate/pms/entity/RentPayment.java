package com.kenyarealestate.pms.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "rent_payments")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RentPayment {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "invoice_id", nullable = false)
    private UUID invoiceId;

    @Column(name = "lease_id", nullable = false)
    private UUID leaseId;

    @Column(name = "payment_id")
    private UUID paymentId;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private PaymentMethod method;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private RentPaymentStatus status = RentPaymentStatus.PENDING;

    @Column(name = "mpesa_receipt", length = 40)
    private String mpesaReceipt;

    @Column(name = "recorded_by")
    private UUID recordedBy;

    @Column(length = 500)
    private String note;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "paid_at")
    private LocalDateTime paidAt;
}
