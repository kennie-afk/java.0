package com.smartseason.inventory.domain;

import com.smartseason.inventory.platform.BaseEntity;
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
@Table(name = "reservations", indexes = {
        @Index(name = "ix_reservations_stock_item_id", columnList = "stock_item_id"),
        @Index(name = "ix_reservations_order_id", columnList = "order_id")
})
public class Reservation extends BaseEntity {

    @Column(name = "stock_item_id", nullable = false)
    private UUID stockItemId;

    @Column(name = "order_id")
    private UUID orderId;

    @Column(name = "quantity", nullable = false)
    private BigDecimal quantity;

    @Column(name = "reserved_at", nullable = false)
    private Instant reservedAt;

    @Column(name = "expires_at")
    private Instant expiresAt;

    @Column(name = "released_at")
    private Instant releasedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private Status status;

    public UUID getStockItemId() { return stockItemId; }
    public void setStockItemId(UUID stockItemId) { this.stockItemId = stockItemId; }

    public UUID getOrderId() { return orderId; }
    public void setOrderId(UUID orderId) { this.orderId = orderId; }

    public BigDecimal getQuantity() { return quantity; }
    public void setQuantity(BigDecimal quantity) { this.quantity = quantity; }

    public Instant getReservedAt() { return reservedAt; }
    public void setReservedAt(Instant reservedAt) { this.reservedAt = reservedAt; }

    public Instant getExpiresAt() { return expiresAt; }
    public void setExpiresAt(Instant expiresAt) { this.expiresAt = expiresAt; }

    public Instant getReleasedAt() { return releasedAt; }
    public void setReleasedAt(Instant releasedAt) { this.releasedAt = releasedAt; }

    public Status getStatus() { return status; }
    public void setStatus(Status status) { this.status = status; }

    public enum Status { HELD, CONSUMED, RELEASED, EXPIRED }

}
