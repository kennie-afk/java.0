package com.smartseason.telemetryingest.domain;

import com.smartseason.telemetryingest.platform.BaseEntity;
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
@Table(name = "telemetry_anomalies", indexes = {
        @Index(name = "ix_telemetry_anomalies_device_id", columnList = "device_id"),
        @Index(name = "ix_telemetry_anomalies_plot_id", columnList = "plot_id")
})
public class TelemetryAnomalyRecord extends BaseEntity {

    @Column(name = "device_id", nullable = false)
    private UUID deviceId;

    @Column(name = "plot_id")
    private UUID plotId;

    @Column(name = "metric", nullable = false)
    private String metric;

    @Column(name = "observed_value", nullable = false)
    private BigDecimal observedValue;

    @Column(name = "expected_min")
    private BigDecimal expectedMin;

    @Column(name = "expected_max")
    private BigDecimal expectedMax;

    @Column(name = "detected_at", nullable = false)
    private Instant detectedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "severity", nullable = false)
    private Severity severity;

    @Column(name = "resolved", nullable = false)
    private Boolean resolved;

    public UUID getDeviceId() { return deviceId; }
    public void setDeviceId(UUID deviceId) { this.deviceId = deviceId; }

    public UUID getPlotId() { return plotId; }
    public void setPlotId(UUID plotId) { this.plotId = plotId; }

    public String getMetric() { return metric; }
    public void setMetric(String metric) { this.metric = metric; }

    public BigDecimal getObservedValue() { return observedValue; }
    public void setObservedValue(BigDecimal observedValue) { this.observedValue = observedValue; }

    public BigDecimal getExpectedMin() { return expectedMin; }
    public void setExpectedMin(BigDecimal expectedMin) { this.expectedMin = expectedMin; }

    public BigDecimal getExpectedMax() { return expectedMax; }
    public void setExpectedMax(BigDecimal expectedMax) { this.expectedMax = expectedMax; }

    public Instant getDetectedAt() { return detectedAt; }
    public void setDetectedAt(Instant detectedAt) { this.detectedAt = detectedAt; }

    public Severity getSeverity() { return severity; }
    public void setSeverity(Severity severity) { this.severity = severity; }

    public Boolean getResolved() { return resolved; }
    public void setResolved(Boolean resolved) { this.resolved = resolved; }

    public enum Severity { LOW, MEDIUM, HIGH }

}
