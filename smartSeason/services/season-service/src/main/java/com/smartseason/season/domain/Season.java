package com.smartseason.season.domain;

import com.smartseason.season.platform.BaseEntity;
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
@Table(name = "seasons", indexes = {
        @Index(name = "ix_seasons_plot_id", columnList = "plot_id"),
        @Index(name = "ix_seasons_farm_id", columnList = "farm_id")
})
public class Season extends BaseEntity {

    @Column(name = "plot_id", nullable = false)
    private UUID plotId;

    @Column(name = "farm_id")
    private UUID farmId;

    @Column(name = "crop_code", nullable = false)
    private String cropCode;

    @Column(name = "variety")
    private String variety;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "expected_harvest_date")
    private LocalDate expectedHarvestDate;

    @Column(name = "actual_harvest_date")
    private LocalDate actualHarvestDate;

    @Column(name = "expected_yield_kg")
    private BigDecimal expectedYieldKg;

    @Column(name = "actual_yield_kg")
    private BigDecimal actualYieldKg;

    @Column(name = "current_stage")
    private String currentStage;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private Status status;

    public UUID getPlotId() { return plotId; }
    public void setPlotId(UUID plotId) { this.plotId = plotId; }

    public UUID getFarmId() { return farmId; }
    public void setFarmId(UUID farmId) { this.farmId = farmId; }

    public String getCropCode() { return cropCode; }
    public void setCropCode(String cropCode) { this.cropCode = cropCode; }

    public String getVariety() { return variety; }
    public void setVariety(String variety) { this.variety = variety; }

    public LocalDate getStartDate() { return startDate; }
    public void setStartDate(LocalDate startDate) { this.startDate = startDate; }

    public LocalDate getExpectedHarvestDate() { return expectedHarvestDate; }
    public void setExpectedHarvestDate(LocalDate expectedHarvestDate) { this.expectedHarvestDate = expectedHarvestDate; }

    public LocalDate getActualHarvestDate() { return actualHarvestDate; }
    public void setActualHarvestDate(LocalDate actualHarvestDate) { this.actualHarvestDate = actualHarvestDate; }

    public BigDecimal getExpectedYieldKg() { return expectedYieldKg; }
    public void setExpectedYieldKg(BigDecimal expectedYieldKg) { this.expectedYieldKg = expectedYieldKg; }

    public BigDecimal getActualYieldKg() { return actualYieldKg; }
    public void setActualYieldKg(BigDecimal actualYieldKg) { this.actualYieldKg = actualYieldKg; }

    public String getCurrentStage() { return currentStage; }
    public void setCurrentStage(String currentStage) { this.currentStage = currentStage; }

    public Status getStatus() { return status; }
    public void setStatus(Status status) { this.status = status; }

    public enum Status { PLANNED, ACTIVE, HARVESTED, CLOSED, ABANDONED }

}
