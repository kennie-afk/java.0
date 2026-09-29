package com.soko.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * Stock that went bad before it sold -- the number a real fermented-milk
 * distributor wants on her dashboard and currently has nowhere to see. Not
 * derived from shelf life automatically: a human records it (a supplier or
 * operator noticing spoiled stock), because "stock past its shelf life" and
 * "stock that was actually wasted" are not the same fact -- some of it might
 * still have sold in time.
 */
@Entity
@Table(name = "wastage_records")
public class WastageRecord {

    @Id @GeneratedValue private UUID id;

    @Column(name = "tenant_id", nullable = false) private UUID tenantId;
    @Column(name = "offer_id", nullable = false) private UUID offerId;
    @Column(name = "product_id", nullable = false) private UUID productId;
    @Column(name = "supplier_id", nullable = false) private UUID supplierId;
    @Column(nullable = false) private int quantity;
    @Column(nullable = false) private String reason = "EXPIRED";
    @Column(name = "value_cents", nullable = false) private long valueCents;
    @Column(name = "recorded_at", nullable = false) private Instant recordedAt = Instant.now();

    public UUID getId() { return id; }
    public void setId(UUID v) { this.id = v; }
    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID v) { this.tenantId = v; }
    public UUID getOfferId() { return offerId; }
    public void setOfferId(UUID v) { this.offerId = v; }
    public UUID getProductId() { return productId; }
    public void setProductId(UUID v) { this.productId = v; }
    public UUID getSupplierId() { return supplierId; }
    public void setSupplierId(UUID v) { this.supplierId = v; }
    public int getQuantity() { return quantity; }
    public void setQuantity(int v) { this.quantity = v; }
    public String getReason() { return reason; }
    public void setReason(String v) { this.reason = v; }
    public long getValueCents() { return valueCents; }
    public void setValueCents(long v) { this.valueCents = v; }
    public Instant getRecordedAt() { return recordedAt; }
    public void setRecordedAt(Instant v) { this.recordedAt = v; }
}
