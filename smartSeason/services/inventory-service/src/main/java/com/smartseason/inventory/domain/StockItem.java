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
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "stock_items", indexes = {
        @Index(name = "ix_stock_items_warehouse_id", columnList = "warehouse_id"),
        @Index(name = "ix_stock_items_commodity_code", columnList = "commodity_code"),
        @Index(name = "ix_stock_items_batch_id", columnList = "batch_id")
})
public class StockItem extends BaseEntity {

    @Column(name = "warehouse_id", nullable = false)
    private UUID warehouseId;

    @Column(name = "commodity_code", nullable = false)
    private String commodityCode;

    @Column(name = "grade")
    private String grade;

    @Column(name = "batch_id")
    private UUID batchId;

    @Column(name = "quantity", nullable = false)
    private BigDecimal quantity;

    @Column(name = "unit", nullable = false)
    private String unit;

    @Column(name = "reserved_quantity", nullable = false)
    private BigDecimal reservedQuantity;

    @Column(name = "expires_on")
    private LocalDate expiresOn;

    @Column(name = "last_counted_at")
    private Instant lastCountedAt;

    public UUID getWarehouseId() { return warehouseId; }
    public void setWarehouseId(UUID warehouseId) { this.warehouseId = warehouseId; }

    public String getCommodityCode() { return commodityCode; }
    public void setCommodityCode(String commodityCode) { this.commodityCode = commodityCode; }

    public String getGrade() { return grade; }
    public void setGrade(String grade) { this.grade = grade; }

    public UUID getBatchId() { return batchId; }
    public void setBatchId(UUID batchId) { this.batchId = batchId; }

    public BigDecimal getQuantity() { return quantity; }
    public void setQuantity(BigDecimal quantity) { this.quantity = quantity; }

    public String getUnit() { return unit; }
    public void setUnit(String unit) { this.unit = unit; }

    public BigDecimal getReservedQuantity() { return reservedQuantity; }
    public void setReservedQuantity(BigDecimal reservedQuantity) { this.reservedQuantity = reservedQuantity; }

    public LocalDate getExpiresOn() { return expiresOn; }
    public void setExpiresOn(LocalDate expiresOn) { this.expiresOn = expiresOn; }

    public Instant getLastCountedAt() { return lastCountedAt; }
    public void setLastCountedAt(Instant lastCountedAt) { this.lastCountedAt = lastCountedAt; }

}
