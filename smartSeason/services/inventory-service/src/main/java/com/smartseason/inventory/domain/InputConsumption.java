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
@Table(name = "input_consumptions", indexes = {
        @Index(name = "ix_input_consumptions_input_issue_id", columnList = "input_issue_id"),
        @Index(name = "ix_input_consumptions_farm_id", columnList = "farm_id"),
        @Index(name = "ix_input_consumptions_plot_id", columnList = "plot_id"),
        @Index(name = "ix_input_consumptions_season_id", columnList = "season_id")
})
public class InputConsumption extends BaseEntity {

    @Column(name = "input_issue_id")
    private UUID inputIssueId;

    @Column(name = "farm_id", nullable = false)
    private UUID farmId;

    @Column(name = "plot_id")
    private UUID plotId;

    @Column(name = "season_id")
    private UUID seasonId;

    @Column(name = "input_code", nullable = false)
    private String inputCode;

    @Column(name = "quantity", nullable = false)
    private BigDecimal quantity;

    @Column(name = "unit", nullable = false)
    private String unit;

    @Column(name = "applied_at", nullable = false)
    private Instant appliedAt;

    @Column(name = "applied_by")
    private UUID appliedBy;

    @Column(name = "area_covered_ha")
    private BigDecimal areaCoveredHa;

    @Column(name = "evidence_url")
    private String evidenceUrl;

    @Column(name = "variance_kg")
    private BigDecimal varianceKg;

    public UUID getInputIssueId() { return inputIssueId; }
    public void setInputIssueId(UUID inputIssueId) { this.inputIssueId = inputIssueId; }

    public UUID getFarmId() { return farmId; }
    public void setFarmId(UUID farmId) { this.farmId = farmId; }

    public UUID getPlotId() { return plotId; }
    public void setPlotId(UUID plotId) { this.plotId = plotId; }

    public UUID getSeasonId() { return seasonId; }
    public void setSeasonId(UUID seasonId) { this.seasonId = seasonId; }

    public String getInputCode() { return inputCode; }
    public void setInputCode(String inputCode) { this.inputCode = inputCode; }

    public BigDecimal getQuantity() { return quantity; }
    public void setQuantity(BigDecimal quantity) { this.quantity = quantity; }

    public String getUnit() { return unit; }
    public void setUnit(String unit) { this.unit = unit; }

    public Instant getAppliedAt() { return appliedAt; }
    public void setAppliedAt(Instant appliedAt) { this.appliedAt = appliedAt; }

    public UUID getAppliedBy() { return appliedBy; }
    public void setAppliedBy(UUID appliedBy) { this.appliedBy = appliedBy; }

    public BigDecimal getAreaCoveredHa() { return areaCoveredHa; }
    public void setAreaCoveredHa(BigDecimal areaCoveredHa) { this.areaCoveredHa = areaCoveredHa; }

    public String getEvidenceUrl() { return evidenceUrl; }
    public void setEvidenceUrl(String evidenceUrl) { this.evidenceUrl = evidenceUrl; }

    public BigDecimal getVarianceKg() { return varianceKg; }
    public void setVarianceKg(BigDecimal varianceKg) { this.varianceKg = varianceKg; }

}
