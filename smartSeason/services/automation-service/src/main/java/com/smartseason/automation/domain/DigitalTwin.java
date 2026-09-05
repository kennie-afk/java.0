package com.smartseason.automation.domain;

import com.smartseason.automation.platform.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "digital_twins", indexes = {
        @Index(name = "ix_digital_twins_plot_id", columnList = "plot_id")
})
public class DigitalTwin extends BaseEntity {

    @Column(name = "plot_id", nullable = false, unique = true)
    private UUID plotId;

    @Column(name = "soil_moisture_pct")
    private BigDecimal soilMoisturePct;

    @Column(name = "soil_temp_c")
    private BigDecimal soilTempC;

    @Column(name = "canopy_index")
    private BigDecimal canopyIndex;

    @Enumerated(EnumType.STRING)
    @Column(name = "irrigation_state", nullable = false)
    private IrrigationState irrigationState;

    @Column(name = "last_irrigated_at")
    private Instant lastIrrigatedAt;

    @Column(name = "updated_from_event_at")
    private Instant updatedFromEventAt;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "state", columnDefinition = "jsonb")
    private String state;

    public UUID getPlotId() { return plotId; }
    public void setPlotId(UUID plotId) { this.plotId = plotId; }

    public BigDecimal getSoilMoisturePct() { return soilMoisturePct; }
    public void setSoilMoisturePct(BigDecimal soilMoisturePct) { this.soilMoisturePct = soilMoisturePct; }

    public BigDecimal getSoilTempC() { return soilTempC; }
    public void setSoilTempC(BigDecimal soilTempC) { this.soilTempC = soilTempC; }

    public BigDecimal getCanopyIndex() { return canopyIndex; }
    public void setCanopyIndex(BigDecimal canopyIndex) { this.canopyIndex = canopyIndex; }

    public IrrigationState getIrrigationState() { return irrigationState; }
    public void setIrrigationState(IrrigationState irrigationState) { this.irrigationState = irrigationState; }

    public Instant getLastIrrigatedAt() { return lastIrrigatedAt; }
    public void setLastIrrigatedAt(Instant lastIrrigatedAt) { this.lastIrrigatedAt = lastIrrigatedAt; }

    public Instant getUpdatedFromEventAt() { return updatedFromEventAt; }
    public void setUpdatedFromEventAt(Instant updatedFromEventAt) { this.updatedFromEventAt = updatedFromEventAt; }

    public String getState() { return state; }
    public void setState(String state) { this.state = state; }

    public enum IrrigationState { IDLE, RUNNING, FAULT }

}
