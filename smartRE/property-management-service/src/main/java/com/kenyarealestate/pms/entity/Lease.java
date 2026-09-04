package com.kenyarealestate.pms.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "leases")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Lease {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "unit_id", nullable = false)
    private UUID unitId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "landlord_id", nullable = false)
    private UUID landlordId;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date")
    private LocalDate endDate;

    @Column(name = "rent_amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal rentAmount;

    @Builder.Default
    @Column(name = "deposit_amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal depositAmount = BigDecimal.ZERO;

    @Builder.Default
    @Column(name = "deposit_held", nullable = false, precision = 14, scale = 2)
    private BigDecimal depositHeld = BigDecimal.ZERO;

    @Builder.Default
    @Column(name = "management_fee_pct", nullable = false, precision = 5, scale = 2)
    private BigDecimal managementFeePct = BigDecimal.ZERO;

    @Builder.Default
    @Column(name = "billing_day", nullable = false)
    private Integer billingDay = 1;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "payment_frequency", nullable = false, length = 16)
    private PaymentFrequency paymentFrequency = PaymentFrequency.MONTHLY;

    @Builder.Default
    @Column(name = "notice_period_days", nullable = false)
    private Integer noticePeriodDays = 30;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private LeaseStatus status = LeaseStatus.DRAFT;

    @Column(name = "terminated_reason", columnDefinition = "TEXT")
    private String terminatedReason;

    @Column(name = "terminated_at")
    private LocalDateTime terminatedAt;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
