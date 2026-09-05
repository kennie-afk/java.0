package com.smartseason.traceability.domain;

import com.smartseason.traceability.platform.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "trace_batches", indexes = {
        @Index(name = "ix_trace_batches_batch_code", columnList = "batch_code"),
        @Index(name = "ix_trace_batches_commodity_code", columnList = "commodity_code"),
        @Index(name = "ix_trace_batches_farm_id", columnList = "farm_id"),
        @Index(name = "ix_trace_batches_plot_id", columnList = "plot_id"),
        @Index(name = "ix_trace_batches_season_id", columnList = "season_id"),
        @Index(name = "ix_trace_batches_current_holder_org_id", columnList = "current_holder_org_id")
})
public class TraceBatch extends BaseEntity {

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

    @Column(name = "origin_county")
    private String originCounty;

    @Column(name = "current_holder_org_id")
    private UUID currentHolderOrgId;

    @Column(name = "quantity_kg")
    private BigDecimal quantityKg;

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

    public String getOriginCounty() { return originCounty; }
    public void setOriginCounty(String originCounty) { this.originCounty = originCounty; }

    public UUID getCurrentHolderOrgId() { return currentHolderOrgId; }
    public void setCurrentHolderOrgId(UUID currentHolderOrgId) { this.currentHolderOrgId = currentHolderOrgId; }

    public BigDecimal getQuantityKg() { return quantityKg; }
    public void setQuantityKg(BigDecimal quantityKg) { this.quantityKg = quantityKg; }

    public Status getStatus() { return status; }
    public void setStatus(Status status) { this.status = status; }

    public enum Status { ACTIVE, CONSUMED, RECALLED }

}
