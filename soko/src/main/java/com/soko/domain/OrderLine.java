package com.soko.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "order_lines")
public class OrderLine {

    @Id @GeneratedValue private UUID id;
    @Column(name = "tenant_id", nullable = false) private UUID tenantId;
    @Column(name = "order_id", nullable = false) private UUID orderId;
    @Column(name = "product_id", nullable = false) private UUID productId;
    @Column(name = "supplier_id") private UUID supplierId;
    @Column(nullable = false) private int quantity;
    @Column(name = "unit_price_cents", nullable = false) private long unitPriceCents;
    @Column(name = "unit_cost_cents", nullable = false) private long unitCostCents;
    @Column(nullable = false) private String status = "PENDING";
    @Column(name = "routing_reason") private String routingReason;
    @Column(name = "created_at", nullable = false) private Instant createdAt = Instant.now();

    public UUID getId() { return id; }
    public void setId(UUID v) { this.id = v; }
    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID v) { this.tenantId = v; }
    public UUID getOrderId() { return orderId; }
    public void setOrderId(UUID v) { this.orderId = v; }
    public UUID getProductId() { return productId; }
    public void setProductId(UUID v) { this.productId = v; }
    public UUID getSupplierId() { return supplierId; }
    public void setSupplierId(UUID v) { this.supplierId = v; }
    public int getQuantity() { return quantity; }
    public void setQuantity(int v) { this.quantity = v; }
    public long getUnitPriceCents() { return unitPriceCents; }
    public void setUnitPriceCents(long v) { this.unitPriceCents = v; }
    public long getUnitCostCents() { return unitCostCents; }
    public void setUnitCostCents(long v) { this.unitCostCents = v; }
    public String getStatus() { return status; }
    public void setStatus(String v) { this.status = v; }
    public String getRoutingReason() { return routingReason; }
    public void setRoutingReason(String v) { this.routingReason = v; }
}
