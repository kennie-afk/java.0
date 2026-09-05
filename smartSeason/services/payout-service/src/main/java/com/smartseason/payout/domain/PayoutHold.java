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
@Table(name = "payout_holds", indexes = {
        @Index(name = "ix_payout_holds_payout_item_id", columnList = "payout_item_id"),
        @Index(name = "ix_payout_holds_payee_id", columnList = "payee_id"),
        @Index(name = "ix_payout_holds_fraud_case_id", columnList = "fraud_case_id")
})
public class PayoutHold extends BaseEntity {

    @Column(name = "payout_item_id")
    private UUID payoutItemId;

    @Column(name = "payee_id", nullable = false)
    private UUID payeeId;

    @Enumerated(EnumType.STRING)
    @Column(name = "reason", nullable = false)
    private Reason reason;

    @Column(name = "fraud_case_id")
    private UUID fraudCaseId;

    @Column(name = "amount")
    private BigDecimal amount;

    @Column(name = "held_at", nullable = false)
    private Instant heldAt;

    @Column(name = "held_by")
    private UUID heldBy;

    @Column(name = "released_at")
    private Instant releasedAt;

    @Column(name = "released_by")
    private UUID releasedBy;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private Status status;

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    public UUID getPayoutItemId() { return payoutItemId; }
    public void setPayoutItemId(UUID payoutItemId) { this.payoutItemId = payoutItemId; }

    public UUID getPayeeId() { return payeeId; }
    public void setPayeeId(UUID payeeId) { this.payeeId = payeeId; }

    public Reason getReason() { return reason; }
    public void setReason(Reason reason) { this.reason = reason; }

    public UUID getFraudCaseId() { return fraudCaseId; }
    public void setFraudCaseId(UUID fraudCaseId) { this.fraudCaseId = fraudCaseId; }

    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }

    public Instant getHeldAt() { return heldAt; }
    public void setHeldAt(Instant heldAt) { this.heldAt = heldAt; }

    public UUID getHeldBy() { return heldBy; }
    public void setHeldBy(UUID heldBy) { this.heldBy = heldBy; }

    public Instant getReleasedAt() { return releasedAt; }
    public void setReleasedAt(Instant releasedAt) { this.releasedAt = releasedAt; }

    public UUID getReleasedBy() { return releasedBy; }
    public void setReleasedBy(UUID releasedBy) { this.releasedBy = releasedBy; }

    public Status getStatus() { return status; }
    public void setStatus(Status status) { this.status = status; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }

    public enum Reason { FRAUD_CASE, KYC_INCOMPLETE, DISPUTE, MANUAL, SANCTIONS }

    public enum Status { ACTIVE, RELEASED, ESCALATED }

}
