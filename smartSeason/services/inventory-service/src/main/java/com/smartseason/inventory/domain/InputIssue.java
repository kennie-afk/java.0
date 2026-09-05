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
@Table(name = "input_issues", indexes = {
        @Index(name = "ix_input_issues_farm_id", columnList = "farm_id"),
        @Index(name = "ix_input_issues_plot_id", columnList = "plot_id"),
        @Index(name = "ix_input_issues_season_id", columnList = "season_id"),
        @Index(name = "ix_input_issues_input_code", columnList = "input_code")
})
public class InputIssue extends BaseEntity {

    @Column(name = "farm_id", nullable = false)
    private UUID farmId;

    @Column(name = "plot_id")
    private UUID plotId;

    @Column(name = "season_id")
    private UUID seasonId;

    @Column(name = "input_code", nullable = false)
    private String inputCode;

    @Column(name = "input_name", nullable = false)
    private String inputName;

    @Column(name = "quantity", nullable = false)
    private BigDecimal quantity;

    @Column(name = "unit", nullable = false)
    private String unit;

    @Column(name = "issued_to")
    private UUID issuedTo;

    @Column(name = "issued_by")
    private UUID issuedBy;

    @Column(name = "issued_at", nullable = false)
    private Instant issuedAt;

    @Column(name = "unit_cost")
    private BigDecimal unitCost;

    @Column(name = "expected_rate_per_ha")
    private BigDecimal expectedRatePerHa;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private Status status;

    public UUID getFarmId() { return farmId; }
    public void setFarmId(UUID farmId) { this.farmId = farmId; }

    public UUID getPlotId() { return plotId; }
    public void setPlotId(UUID plotId) { this.plotId = plotId; }

    public UUID getSeasonId() { return seasonId; }
    public void setSeasonId(UUID seasonId) { this.seasonId = seasonId; }

    public String getInputCode() { return inputCode; }
    public void setInputCode(String inputCode) { this.inputCode = inputCode; }

    public String getInputName() { return inputName; }
    public void setInputName(String inputName) { this.inputName = inputName; }

    public BigDecimal getQuantity() { return quantity; }
    public void setQuantity(BigDecimal quantity) { this.quantity = quantity; }

    public String getUnit() { return unit; }
    public void setUnit(String unit) { this.unit = unit; }

    public UUID getIssuedTo() { return issuedTo; }
    public void setIssuedTo(UUID issuedTo) { this.issuedTo = issuedTo; }

    public UUID getIssuedBy() { return issuedBy; }
    public void setIssuedBy(UUID issuedBy) { this.issuedBy = issuedBy; }

    public Instant getIssuedAt() { return issuedAt; }
    public void setIssuedAt(Instant issuedAt) { this.issuedAt = issuedAt; }

    public BigDecimal getUnitCost() { return unitCost; }
    public void setUnitCost(BigDecimal unitCost) { this.unitCost = unitCost; }

    public BigDecimal getExpectedRatePerHa() { return expectedRatePerHa; }
    public void setExpectedRatePerHa(BigDecimal expectedRatePerHa) { this.expectedRatePerHa = expectedRatePerHa; }

    public Status getStatus() { return status; }
    public void setStatus(Status status) { this.status = status; }

    public enum Status { ISSUED, RECONCILED, VARIANCE }

}
