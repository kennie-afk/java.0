package com.smartseason.season.domain;

import com.smartseason.season.platform.BaseEntity;
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
@Table(name = "planting_plans", indexes = {
        @Index(name = "ix_planting_plans_season_id", columnList = "season_id")
})
public class PlantingPlan extends BaseEntity {

    @Column(name = "season_id", nullable = false)
    private UUID seasonId;

    @Column(name = "seed_rate_kg_ha")
    private BigDecimal seedRateKgHa;

    @Column(name = "spacing_cm")
    private String spacingCm;

    @Column(name = "target_population")
    private Integer targetPopulation;

    @Column(name = "fertiliser_plan", columnDefinition = "TEXT")
    private String fertiliserPlan;

    @Column(name = "irrigation_plan", columnDefinition = "TEXT")
    private String irrigationPlan;

    @Column(name = "approved_by")
    private UUID approvedBy;

    @Column(name = "approved_at")
    private Instant approvedAt;

    public UUID getSeasonId() { return seasonId; }
    public void setSeasonId(UUID seasonId) { this.seasonId = seasonId; }

    public BigDecimal getSeedRateKgHa() { return seedRateKgHa; }
    public void setSeedRateKgHa(BigDecimal seedRateKgHa) { this.seedRateKgHa = seedRateKgHa; }

    public String getSpacingCm() { return spacingCm; }
    public void setSpacingCm(String spacingCm) { this.spacingCm = spacingCm; }

    public Integer getTargetPopulation() { return targetPopulation; }
    public void setTargetPopulation(Integer targetPopulation) { this.targetPopulation = targetPopulation; }

    public String getFertiliserPlan() { return fertiliserPlan; }
    public void setFertiliserPlan(String fertiliserPlan) { this.fertiliserPlan = fertiliserPlan; }

    public String getIrrigationPlan() { return irrigationPlan; }
    public void setIrrigationPlan(String irrigationPlan) { this.irrigationPlan = irrigationPlan; }

    public UUID getApprovedBy() { return approvedBy; }
    public void setApprovedBy(UUID approvedBy) { this.approvedBy = approvedBy; }

    public Instant getApprovedAt() { return approvedAt; }
    public void setApprovedAt(Instant approvedAt) { this.approvedAt = approvedAt; }

}
