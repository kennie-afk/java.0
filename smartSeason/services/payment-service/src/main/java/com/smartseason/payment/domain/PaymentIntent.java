package com.smartseason.payment.domain;

import com.smartseason.payment.platform.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "payment_intents", indexes = {
        @Index(name = "ix_payment_intents_reference", columnList = "reference"),
        @Index(name = "ix_payment_intents_order_id", columnList = "order_id"),
        @Index(name = "ix_payment_intents_payer_org_id", columnList = "payer_org_id"),
        @Index(name = "ix_payment_intents_payee_org_id", columnList = "payee_org_id"),
        @Index(name = "ix_payment_intents_idempotency_key", columnList = "idempotency_key"),
        @Index(name = "ix_payment_intents_provider_ref", columnList = "provider_ref")
})
public class PaymentIntent extends BaseEntity {

    @Column(name = "reference", nullable = false, unique = true)
    private String reference;

    @Column(name = "order_id")
    private UUID orderId;

    @Column(name = "payer_org_id")
    private UUID payerOrgId;

    @Column(name = "payee_org_id")
    private UUID payeeOrgId;

    @Column(name = "amount", nullable = false)
    private BigDecimal amount;

    @Column(name = "currency", nullable = false)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(name = "method", nullable = false)
    private Method method;

    @Enumerated(EnumType.STRING)
    @Column(name = "purpose", nullable = false)
    private Purpose purpose;

    @Column(name = "payer_phone")
    private String payerPhone;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private Status status;

    @Column(name = "idempotency_key", nullable = false, unique = true)
    private String idempotencyKey;

    @Column(name = "initiated_at", nullable = false)
    private Instant initiatedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "failure_reason")
    private String failureReason;

    @Column(name = "provider_ref")
    private String providerRef;

    @Column(name = "escrow", nullable = false)
    private Boolean escrow;

    public String getReference() { return reference; }
    public void setReference(String reference) { this.reference = reference; }

    public UUID getOrderId() { return orderId; }
    public void setOrderId(UUID orderId) { this.orderId = orderId; }

    public UUID getPayerOrgId() { return payerOrgId; }
    public void setPayerOrgId(UUID payerOrgId) { this.payerOrgId = payerOrgId; }

    public UUID getPayeeOrgId() { return payeeOrgId; }
    public void setPayeeOrgId(UUID payeeOrgId) { this.payeeOrgId = payeeOrgId; }

    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }

    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }

    public Method getMethod() { return method; }
    public void setMethod(Method method) { this.method = method; }

    public Purpose getPurpose() { return purpose; }
    public void setPurpose(Purpose purpose) { this.purpose = purpose; }

    public String getPayerPhone() { return payerPhone; }
    public void setPayerPhone(String payerPhone) { this.payerPhone = payerPhone; }

    public Status getStatus() { return status; }
    public void setStatus(Status status) { this.status = status; }

    public String getIdempotencyKey() { return idempotencyKey; }
    public void setIdempotencyKey(String idempotencyKey) { this.idempotencyKey = idempotencyKey; }

    public Instant getInitiatedAt() { return initiatedAt; }
    public void setInitiatedAt(Instant initiatedAt) { this.initiatedAt = initiatedAt; }

    public Instant getCompletedAt() { return completedAt; }
    public void setCompletedAt(Instant completedAt) { this.completedAt = completedAt; }

    public String getFailureReason() { return failureReason; }
    public void setFailureReason(String failureReason) { this.failureReason = failureReason; }

    public String getProviderRef() { return providerRef; }
    public void setProviderRef(String providerRef) { this.providerRef = providerRef; }

    public Boolean getEscrow() { return escrow; }
    public void setEscrow(Boolean escrow) { this.escrow = escrow; }

    public enum Method { MPESA_STK, MPESA_C2B, MPESA_B2C, CARD, WALLET, BANK }

    public enum Purpose { ORDER, WALLET_TOPUP, PAYOUT, WAGE, REFUND, FEE }

    public enum Status { CREATED, PENDING, PROCESSING, SUCCEEDED, FAILED, CANCELLED, REFUNDED }

}
