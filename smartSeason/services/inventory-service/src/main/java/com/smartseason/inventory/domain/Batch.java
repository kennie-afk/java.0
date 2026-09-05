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
@Table(name = "batches", indexes = {
        @Index(name = "ix_batches_batch_code", columnList = "batch_code"),
        @Index(name = "ix_batches_commodity_code", columnList = "commodity_code"),
        @Index(name = "ix_batches_farm_id", columnList = "farm_id"),
        @Index(name = "ix_batches_plot_id", columnList = "plot_id"),
        @Index(name = "ix_batches_season_id", columnList = "season_id"),
        @Index(name = "ix_batches_warehouse_id", columnList = "warehouse_id")
})
public class Batch extends BaseEntity {

    @Column(name = "batch_code", nullable = false, unique = true)
    private String batchCode;

    @Column(name = "commodity_code", nullable = false)
    private String commodityCode;

    @Column(name = "farm_id")
    private UUID farmId;

    @Column(name = "plot_id")
    private UUID plotId;

    @Column(name = "season_id")
    private UUID seasonId;

    @Column(name = "harvested_on")
    private LocalDate harvestedOn;

    @Column(name = "received_at")
    private Instant receivedAt;

    @Column(name = "warehouse_id")
    private UUID warehouseId;

    @Column(name = "gross_weight_kg")
    private BigDecimal grossWeightKg;

    @Column(name = "net_weight_kg")
    private BigDecimal netWeightKg;

    @Column(name = "grade")
    private String grade;

    @Column(name = "moisture_pct")
    private BigDecimal moisturePct;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private Status status;

    public String getBatchCode() { return batchCode; }
    public void setBatchCode(String batchCode) { this.batchCode = batchCode; }

    public String getCommodityCode() { return commodityCode; }
    public void setCommodityCode(String commodityCode) { this.commodityCode = commodityCode; }

    public UUID getFarmId() { return farmId; }
    public void setFarmId(UUID farmId) { this.farmId = farmId; }

    public UUID getPlotId() { return plotId; }
    public void setPlotId(UUID plotId) { this.plotId = plotId; }

    public UUID getSeasonId() { return seasonId; }
    public void setSeasonId(UUID seasonId) { this.seasonId = seasonId; }

    public LocalDate getHarvestedOn() { return harvestedOn; }
    public void setHarvestedOn(LocalDate harvestedOn) { this.harvestedOn = harvestedOn; }

    public Instant getReceivedAt() { return receivedAt; }
    public void setReceivedAt(Instant receivedAt) { this.receivedAt = receivedAt; }

    public UUID getWarehouseId() { return warehouseId; }
    public void setWarehouseId(UUID warehouseId) { this.warehouseId = warehouseId; }

    public BigDecimal getGrossWeightKg() { return grossWeightKg; }
    public void setGrossWeightKg(BigDecimal grossWeightKg) { this.grossWeightKg = grossWeightKg; }

    public BigDecimal getNetWeightKg() { return netWeightKg; }
    public void setNetWeightKg(BigDecimal netWeightKg) { this.netWeightKg = netWeightKg; }

    public String getGrade() { return grade; }
    public void setGrade(String grade) { this.grade = grade; }

    public BigDecimal getMoisturePct() { return moisturePct; }
    public void setMoisturePct(BigDecimal moisturePct) { this.moisturePct = moisturePct; }

    public Status getStatus() { return status; }
    public void setStatus(Status status) { this.status = status; }

    public enum Status { HARVESTED, IN_TRANSIT, RECEIVED, GRADED, RESERVED, DISPATCHED, REJECTED }

}
