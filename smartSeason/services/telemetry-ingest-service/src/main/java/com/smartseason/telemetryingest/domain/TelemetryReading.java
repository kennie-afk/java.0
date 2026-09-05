package com.smartseason.telemetryingest.domain;

import com.smartseason.telemetryingest.platform.BaseEntity;
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
@Table(name = "telemetry_readings", indexes = {
        @Index(name = "ix_telemetry_readings_device_id", columnList = "device_id"),
        @Index(name = "ix_telemetry_readings_plot_id", columnList = "plot_id"),
        @Index(name = "ix_telemetry_readings_metric", columnList = "metric")
})
public class TelemetryReading extends BaseEntity {

    @Column(name = "device_id", nullable = false)
    private UUID deviceId;

    @Column(name = "plot_id")
    private UUID plotId;

    @Column(name = "metric", nullable = false)
    private String metric;

    @Column(name = "value", nullable = false)
    private BigDecimal value;

    @Column(name = "unit")
    private String unit;

    @Column(name = "recorded_at", nullable = false)
    private Instant recordedAt;

    @Column(name = "received_at", nullable = false)
    private Instant receivedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "quality", nullable = false)
    private Quality quality;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "raw", columnDefinition = "jsonb")
    private String raw;

    public UUID getDeviceId() { return deviceId; }
    public void setDeviceId(UUID deviceId) { this.deviceId = deviceId; }

    public UUID getPlotId() { return plotId; }
    public void setPlotId(UUID plotId) { this.plotId = plotId; }

    public String getMetric() { return metric; }
    public void setMetric(String metric) { this.metric = metric; }

    public BigDecimal getValue() { return value; }
    public void setValue(BigDecimal value) { this.value = value; }

    public String getUnit() { return unit; }
    public void setUnit(String unit) { this.unit = unit; }

    public Instant getRecordedAt() { return recordedAt; }
    public void setRecordedAt(Instant recordedAt) { this.recordedAt = recordedAt; }

    public Instant getReceivedAt() { return receivedAt; }
    public void setReceivedAt(Instant receivedAt) { this.receivedAt = receivedAt; }

    public Quality getQuality() { return quality; }
    public void setQuality(Quality quality) { this.quality = quality; }

    public String getRaw() { return raw; }
    public void setRaw(String raw) { this.raw = raw; }

    public enum Quality { GOOD, SUSPECT, BAD }

}
