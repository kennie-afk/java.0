package com.soko.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** What a distributor tenant owes Soko-the-platform for one billing period. */
@Entity
@Table(name = "invoices")
public class Invoice {

    public enum Status { ISSUED, PAID, VOID }

    @Id @GeneratedValue private UUID id;

    @Column(name = "tenant_id", nullable = false) private UUID tenantId;
    @Column(nullable = false) private String reference;
    @Column(name = "period_start", nullable = false) private Instant periodStart;
    @Column(name = "period_end", nullable = false) private Instant periodEnd;
    @Column(name = "subscription_fee_cents", nullable = false) private long subscriptionFeeCents;
    @Column(name = "commission_cents", nullable = false) private long commissionCents;
    @Column(name = "total_cents", nullable = false) private long totalCents;
    @Column(nullable = false) private String status = Status.ISSUED.name();
    @Column(name = "issued_at", nullable = false) private Instant issuedAt = Instant.now();
    @Column(name = "due_at", nullable = false) private Instant dueAt;
    @Column(name = "paid_at") private Instant paidAt;

    public UUID getId() { return id; }
    public void setId(UUID v) { this.id = v; }
    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID v) { this.tenantId = v; }
    public String getReference() { return reference; }
    public void setReference(String v) { this.reference = v; }
    public Instant getPeriodStart() { return periodStart; }
    public void setPeriodStart(Instant v) { this.periodStart = v; }
    public Instant getPeriodEnd() { return periodEnd; }
    public void setPeriodEnd(Instant v) { this.periodEnd = v; }
    public long getSubscriptionFeeCents() { return subscriptionFeeCents; }
    public void setSubscriptionFeeCents(long v) { this.subscriptionFeeCents = v; }
    public long getCommissionCents() { return commissionCents; }
    public void setCommissionCents(long v) { this.commissionCents = v; }
    public long getTotalCents() { return totalCents; }
    public void setTotalCents(long v) { this.totalCents = v; }
    public Status getStatus() { return Status.valueOf(status); }
    public void setStatus(Status v) { this.status = v.name(); }
    public Instant getIssuedAt() { return issuedAt; }
    public void setIssuedAt(Instant v) { this.issuedAt = v; }
    public Instant getDueAt() { return dueAt; }
    public void setDueAt(Instant v) { this.dueAt = v; }
    public Instant getPaidAt() { return paidAt; }
    public void setPaidAt(Instant v) { this.paidAt = v; }
}
