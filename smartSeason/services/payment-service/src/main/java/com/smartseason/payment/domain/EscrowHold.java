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
@Table(name = "escrow_holds", indexes = {
        @Index(name = "ix_escrow_holds_payment_intent_id", columnList = "payment_intent_id"),
        @Index(name = "ix_escrow_holds_order_id", columnList = "order_id")
})
public class EscrowHold extends BaseEntity {

    @Column(name = "payment_intent_id", nullable = false)
    private UUID paymentIntentId;

    @Column(name = "order_id", nullable = false)
    private UUID orderId;

    @Column(name = "amount", nullable = false)
    private BigDecimal amount;

    @Column(name = "currency", nullable = false)
    private String currency;

    @Column(name = "held_at", nullable = false)
    private Instant heldAt;

    @Column(name = "release_due_at")
    private Instant releaseDueAt;

    @Column(name = "released_at")
    private Instant releasedAt;

    @Column(name = "released_to")
    private UUID releasedTo;

    @Column(name = "refunded_at")
    private Instant refundedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private Status status;

    @Column(name = "release_condition")
    private String releaseCondition;

    public UUID getPaymentIntentId() { return paymentIntentId; }
    public void setPaymentIntentId(UUID paymentIntentId) { this.paymentIntentId = paymentIntentId; }

    public UUID getOrderId() { return orderId; }
    public void setOrderId(UUID orderId) { this.orderId = orderId; }

    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }

    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }

    public Instant getHeldAt() { return heldAt; }
    public void setHeldAt(Instant heldAt) { this.heldAt = heldAt; }

    public Instant getReleaseDueAt() { return releaseDueAt; }
    public void setReleaseDueAt(Instant releaseDueAt) { this.releaseDueAt = releaseDueAt; }

    public Instant getReleasedAt() { return releasedAt; }
    public void setReleasedAt(Instant releasedAt) { this.releasedAt = releasedAt; }

    public UUID getReleasedTo() { return releasedTo; }
    public void setReleasedTo(UUID releasedTo) { this.releasedTo = releasedTo; }

    public Instant getRefundedAt() { return refundedAt; }
    public void setRefundedAt(Instant refundedAt) { this.refundedAt = refundedAt; }

    public Status getStatus() { return status; }
    public void setStatus(Status status) { this.status = status; }

    public String getReleaseCondition() { return releaseCondition; }
    public void setReleaseCondition(String releaseCondition) { this.releaseCondition = releaseCondition; }

    public enum Status { HELD, RELEASED, REFUNDED, DISPUTED }

}
