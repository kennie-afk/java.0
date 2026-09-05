package com.smartseason.payout.domain;

import com.smartseason.payout.platform.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "settlements", indexes = {
        @Index(name = "ix_settlements_settlement_number", columnList = "settlement_number"),
        @Index(name = "ix_settlements_payee_org_id", columnList = "payee_org_id"),
        @Index(name = "ix_settlements_payee_user_id", columnList = "payee_user_id"),
        @Index(name = "ix_settlements_order_id", columnList = "order_id")
})
public class Settlement extends BaseEntity {

    @Column(name = "settlement_number", nullable = false, unique = true)
    private String settlementNumber;

    @Column(name = "payee_org_id")
    private UUID payeeOrgId;

    @Column(name = "payee_user_id")
    private UUID payeeUserId;

    @Column(name = "order_id")
    private UUID orderId;

    @Column(name = "gross_amount", nullable = false)
    private BigDecimal grossAmount;

    @Column(name = "commission", nullable = false)
    private BigDecimal commission;

    @Column(name = "fees", nullable = false)
    private BigDecimal fees;

    @Column(name = "net_amount", nullable = false)
    private BigDecimal netAmount;

    @Column(name = "currency", nullable = false)
    private String currency;

    @Column(name = "period_start")
    private LocalDate periodStart;

    @Column(name = "period_end")
    private LocalDate periodEnd;

    @Column(name = "due_at")
    private Instant dueAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private Status status;

    @Column(name = "approved_by")
    private UUID approvedBy;

    public String getSettlementNumber() { return settlementNumber; }
    public void setSettlementNumber(String settlementNumber) { this.settlementNumber = settlementNumber; }

    public UUID getPayeeOrgId() { return payeeOrgId; }
    public void setPayeeOrgId(UUID payeeOrgId) { this.payeeOrgId = payeeOrgId; }

    public UUID getPayeeUserId() { return payeeUserId; }
    public void setPayeeUserId(UUID payeeUserId) { this.payeeUserId = payeeUserId; }

    public UUID getOrderId() { return orderId; }
    public void setOrderId(UUID orderId) { this.orderId = orderId; }

    public BigDecimal getGrossAmount() { return grossAmount; }
    public void setGrossAmount(BigDecimal grossAmount) { this.grossAmount = grossAmount; }

    public BigDecimal getCommission() { return commission; }
    public void setCommission(BigDecimal commission) { this.commission = commission; }

    public BigDecimal getFees() { return fees; }
    public void setFees(BigDecimal fees) { this.fees = fees; }

    public BigDecimal getNetAmount() { return netAmount; }
    public void setNetAmount(BigDecimal netAmount) { this.netAmount = netAmount; }

    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }

    public LocalDate getPeriodStart() { return periodStart; }
    public void setPeriodStart(LocalDate periodStart) { this.periodStart = periodStart; }

    public LocalDate getPeriodEnd() { return periodEnd; }
    public void setPeriodEnd(LocalDate periodEnd) { this.periodEnd = periodEnd; }

    public Instant getDueAt() { return dueAt; }
    public void setDueAt(Instant dueAt) { this.dueAt = dueAt; }

    public Status getStatus() { return status; }
    public void setStatus(Status status) { this.status = status; }

    public UUID getApprovedBy() { return approvedBy; }
    public void setApprovedBy(UUID approvedBy) { this.approvedBy = approvedBy; }

    public enum Status { DRAFT, APPROVED, SCHEDULED, PAID, HELD, FAILED, CANCELLED }

}
