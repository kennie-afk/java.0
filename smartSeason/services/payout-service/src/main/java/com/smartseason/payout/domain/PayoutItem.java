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
import java.util.UUID;

@Entity
@Table(name = "payout_items", indexes = {
        @Index(name = "ix_payout_items_batch_id", columnList = "batch_id"),
        @Index(name = "ix_payout_items_settlement_id", columnList = "settlement_id"),
        @Index(name = "ix_payout_items_payee_id", columnList = "payee_id"),
        @Index(name = "ix_payout_items_payment_intent_id", columnList = "payment_intent_id"),
        @Index(name = "ix_payout_items_idempotency_key", columnList = "idempotency_key")
})
public class PayoutItem extends BaseEntity {

    @Column(name = "batch_id")
    private UUID batchId;

    @Column(name = "settlement_id")
    private UUID settlementId;

    @Enumerated(EnumType.STRING)
    @Column(name = "payee_type", nullable = false)
    private PayeeType payeeType;

    @Column(name = "payee_id", nullable = false)
    private UUID payeeId;

    @Column(name = "payee_name")
    private String payeeName;

    @Column(name = "payee_phone")
    private String payeePhone;

    @Column(name = "amount", nullable = false)
    private BigDecimal amount;

    @Column(name = "currency", nullable = false)
    private String currency;

    @Column(name = "payment_intent_id")
    private UUID paymentIntentId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private Status status;

    @Column(name = "failure_reason")
    private String failureReason;

    @Column(name = "sent_at")
    private Instant sentAt;

    @Column(name = "paid_at")
    private Instant paidAt;

    @Column(name = "idempotency_key", unique = true)
    private String idempotencyKey;

    public UUID getBatchId() { return batchId; }
    public void setBatchId(UUID batchId) { this.batchId = batchId; }

    public UUID getSettlementId() { return settlementId; }
    public void setSettlementId(UUID settlementId) { this.settlementId = settlementId; }

    public PayeeType getPayeeType() { return payeeType; }
    public void setPayeeType(PayeeType payeeType) { this.payeeType = payeeType; }

    public UUID getPayeeId() { return payeeId; }
    public void setPayeeId(UUID payeeId) { this.payeeId = payeeId; }

    public String getPayeeName() { return payeeName; }
    public void setPayeeName(String payeeName) { this.payeeName = payeeName; }

    public String getPayeePhone() { return payeePhone; }
    public void setPayeePhone(String payeePhone) { this.payeePhone = payeePhone; }

    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }

    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }

    public UUID getPaymentIntentId() { return paymentIntentId; }
    public void setPaymentIntentId(UUID paymentIntentId) { this.paymentIntentId = paymentIntentId; }

    public Status getStatus() { return status; }
    public void setStatus(Status status) { this.status = status; }

    public String getFailureReason() { return failureReason; }
    public void setFailureReason(String failureReason) { this.failureReason = failureReason; }

    public Instant getSentAt() { return sentAt; }
    public void setSentAt(Instant sentAt) { this.sentAt = sentAt; }

    public Instant getPaidAt() { return paidAt; }
    public void setPaidAt(Instant paidAt) { this.paidAt = paidAt; }

    public String getIdempotencyKey() { return idempotencyKey; }
    public void setIdempotencyKey(String idempotencyKey) { this.idempotencyKey = idempotencyKey; }

    public enum PayeeType { WORKER, FARMER, ORG, DRIVER }

    public enum Status { PENDING, SENT, PAID, HELD, FAILED, REVERSED }

}
