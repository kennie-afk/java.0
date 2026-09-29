package com.soko.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * One row per plan a tenant has ever been on. Exactly one row per tenant has
 * {@code endedAt == null} at a time (the active plan); changing plans ends
 * the current row and inserts a new one rather than mutating it in place, so
 * "what plan was tenant X on when order Y was placed" stays answerable.
 */
@Entity
@Table(name = "subscriptions")
public class Subscription {

    @Id @GeneratedValue private UUID id;

    @Column(name = "tenant_id", nullable = false) private UUID tenantId;
    @Column(nullable = false) private String plan;
    @Column(name = "monthly_fee_cents", nullable = false) private long monthlyFeeCents;
    @Column(name = "commission_bps", nullable = false) private int commissionBps;
    @Column(name = "started_at", nullable = false) private Instant startedAt = Instant.now();
    @Column(name = "ended_at") private Instant endedAt;

    public UUID getId() { return id; }
    public void setId(UUID v) { this.id = v; }
    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID v) { this.tenantId = v; }
    public String getPlan() { return plan; }
    public void setPlan(String v) { this.plan = v; }
    public long getMonthlyFeeCents() { return monthlyFeeCents; }
    public void setMonthlyFeeCents(long v) { this.monthlyFeeCents = v; }
    public int getCommissionBps() { return commissionBps; }
    public void setCommissionBps(int v) { this.commissionBps = v; }
    public Instant getStartedAt() { return startedAt; }
    public void setStartedAt(Instant v) { this.startedAt = v; }
    public Instant getEndedAt() { return endedAt; }
    public void setEndedAt(Instant v) { this.endedAt = v; }
}
