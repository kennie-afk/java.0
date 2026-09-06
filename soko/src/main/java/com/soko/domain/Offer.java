package com.soko.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "offers")
public class Offer {

    @Id @GeneratedValue private UUID id;

    @Column(name = "tenant_id", nullable = false) private UUID tenantId;
    @Column(name = "supplier_id", nullable = false) private UUID supplierId;
    @Column(name = "product_id", nullable = false) private UUID productId;
    @Column(name = "cost_cents", nullable = false) private long costCents;
    @Column(name = "available_qty", nullable = false) private int availableQty;
    @Column(name = "harvested_at") private Instant harvestedAt;
    @Column(nullable = false) private String status = "ACTIVE";
    @Column(name = "created_at", nullable = false) private Instant createdAt = Instant.now();

    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID v) { this.tenantId = v; }
    public UUID getSupplierId() { return supplierId; }
    public void setSupplierId(UUID v) { this.supplierId = v; }
    public UUID getProductId() { return productId; }
    public void setProductId(UUID v) { this.productId = v; }
    public long getCostCents() { return costCents; }
    public void setCostCents(long v) { this.costCents = v; }
    public int getAvailableQty() { return availableQty; }
    public void setAvailableQty(int v) { this.availableQty = v; }
    public Instant getHarvestedAt() { return harvestedAt; }
    public void setHarvestedAt(Instant v) { this.harvestedAt = v; }
    public String getStatus() { return status; }
    public void setStatus(String v) { this.status = v; }

    public UUID getId() { return id; }
    public void setId(UUID v) { this.id = v; }
}
