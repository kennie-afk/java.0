package com.soko.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "orders")
public class SalesOrder {

    @Id @GeneratedValue private UUID id;
    @Column(name = "tenant_id", nullable = false) private UUID tenantId;
    @Column(nullable = false) private String reference;
    @Column(name = "customer_id", nullable = false) private UUID customerId;
    @Column(nullable = false) private String status = "ROUTED";
    @Column(name = "revenue_cents", nullable = false) private long revenueCents;
    @Column(name = "cost_cents", nullable = false) private long costCents;
    @Column(name = "margin_cents", nullable = false) private long marginCents;
    @Column(name = "placed_at", nullable = false) private Instant placedAt = Instant.now();

    @OneToMany(mappedBy = "orderId", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<OrderLine> lines = new ArrayList<>();

    public UUID getId() { return id; }
    public void setId(UUID v) { this.id = v; }
    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID v) { this.tenantId = v; }
    public String getReference() { return reference; }
    public void setReference(String v) { this.reference = v; }
    public UUID getCustomerId() { return customerId; }
    public void setCustomerId(UUID v) { this.customerId = v; }
    public String getStatus() { return status; }
    public void setStatus(String v) { this.status = v; }
    public long getRevenueCents() { return revenueCents; }
    public void setRevenueCents(long v) { this.revenueCents = v; }
    public long getCostCents() { return costCents; }
    public void setCostCents(long v) { this.costCents = v; }
    public long getMarginCents() { return marginCents; }
    public void setMarginCents(long v) { this.marginCents = v; }
    public Instant getPlacedAt() { return placedAt; }
    public void setPlacedAt(Instant v) { this.placedAt = v; }
    public List<OrderLine> getLines() { return lines; }
    public void setLines(List<OrderLine> v) { this.lines = v; }
}
