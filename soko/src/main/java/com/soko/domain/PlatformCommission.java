package com.soko.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * The cut Soko-the-platform takes on one order, snapshotted at the plan and
 * rate that actually applied when the order was placed -- a later plan
 * change must never rewrite what an already-placed order owes.
 */
@Entity
@Table(name = "platform_commissions")
public class PlatformCommission {

    public enum Status { ACCRUED, INVOICED, VOIDED }

    @Id @GeneratedValue private UUID id;

    @Column(name = "tenant_id", nullable = false) private UUID tenantId;
    @Column(name = "order_id", nullable = false) private UUID orderId;
    @Column(nullable = false) private String plan;
    @Column(name = "commission_bps", nullable = false) private int commissionBps;
    @Column(name = "gross_cents", nullable = false) private long grossCents;
    @Column(name = "commission_cents", nullable = false) private long commissionCents;
    @Column(nullable = false) private String status = Status.ACCRUED.name();
    @Column(name = "invoice_id") private UUID invoiceId;
    @Column(name = "created_at", nullable = false) private Instant createdAt = Instant.now();

    public UUID getId() { return id; }
    public void setId(UUID v) { this.id = v; }
    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID v) { this.tenantId = v; }
    public UUID getOrderId() { return orderId; }
    public void setOrderId(UUID v) { this.orderId = v; }
    public String getPlan() { return plan; }
    public void setPlan(String v) { this.plan = v; }
    public int getCommissionBps() { return commissionBps; }
    public void setCommissionBps(int v) { this.commissionBps = v; }
    public long getGrossCents() { return grossCents; }
    public void setGrossCents(long v) { this.grossCents = v; }
    public long getCommissionCents() { return commissionCents; }
    public void setCommissionCents(long v) { this.commissionCents = v; }
    public Status getStatus() { return Status.valueOf(status); }
    public void setStatus(Status v) { this.status = v.name(); }
    public UUID getInvoiceId() { return invoiceId; }
    public void setInvoiceId(UUID v) { this.invoiceId = v; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant v) { this.createdAt = v; }
}
