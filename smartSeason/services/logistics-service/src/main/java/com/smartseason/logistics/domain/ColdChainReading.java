package com.smartseason.logistics.domain;

import com.smartseason.logistics.platform.BaseEntity;
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
@Table(name = "cold_chain_readings", indexes = {
        @Index(name = "ix_cold_chain_readings_transport_job_id", columnList = "transport_job_id")
})
public class ColdChainReading extends BaseEntity {

    @Column(name = "transport_job_id", nullable = false)
    private UUID transportJobId;

    @Column(name = "recorded_at", nullable = false)
    private Instant recordedAt;

    @Column(name = "temperature_c", nullable = false)
    private BigDecimal temperatureC;

    @Column(name = "humidity_pct")
    private BigDecimal humidityPct;

    @Column(name = "device_id")
    private String deviceId;

    @Column(name = "breach", nullable = false)
    private Boolean breach;

    public UUID getTransportJobId() { return transportJobId; }
    public void setTransportJobId(UUID transportJobId) { this.transportJobId = transportJobId; }

    public Instant getRecordedAt() { return recordedAt; }
    public void setRecordedAt(Instant recordedAt) { this.recordedAt = recordedAt; }

    public BigDecimal getTemperatureC() { return temperatureC; }
    public void setTemperatureC(BigDecimal temperatureC) { this.temperatureC = temperatureC; }

    public BigDecimal getHumidityPct() { return humidityPct; }
    public void setHumidityPct(BigDecimal humidityPct) { this.humidityPct = humidityPct; }

    public String getDeviceId() { return deviceId; }
    public void setDeviceId(String deviceId) { this.deviceId = deviceId; }

    public Boolean getBreach() { return breach; }
    public void setBreach(Boolean breach) { this.breach = breach; }

}
