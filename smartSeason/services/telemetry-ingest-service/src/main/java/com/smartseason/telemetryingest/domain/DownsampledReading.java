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
@Table(name = "downsampled_readings", indexes = {
        @Index(name = "ix_downsampled_readings_device_id", columnList = "device_id")
})
public class DownsampledReading extends BaseEntity {

    @Column(name = "device_id", nullable = false)
    private UUID deviceId;

    @Column(name = "metric", nullable = false)
    private String metric;

    @Column(name = "bucket_start", nullable = false)
    private Instant bucketStart;

    @Column(name = "bucket_minutes", nullable = false)
    private Integer bucketMinutes;

    @Column(name = "avg_value")
    private BigDecimal avgValue;

    @Column(name = "min_value")
    private BigDecimal minValue;

    @Column(name = "max_value")
    private BigDecimal maxValue;

    @Column(name = "sample_count", nullable = false)
    private Integer sampleCount;

    public UUID getDeviceId() { return deviceId; }
    public void setDeviceId(UUID deviceId) { this.deviceId = deviceId; }

    public String getMetric() { return metric; }
    public void setMetric(String metric) { this.metric = metric; }

    public Instant getBucketStart() { return bucketStart; }
    public void setBucketStart(Instant bucketStart) { this.bucketStart = bucketStart; }

    public Integer getBucketMinutes() { return bucketMinutes; }
    public void setBucketMinutes(Integer bucketMinutes) { this.bucketMinutes = bucketMinutes; }

    public BigDecimal getAvgValue() { return avgValue; }
    public void setAvgValue(BigDecimal avgValue) { this.avgValue = avgValue; }

    public BigDecimal getMinValue() { return minValue; }
    public void setMinValue(BigDecimal minValue) { this.minValue = minValue; }

    public BigDecimal getMaxValue() { return maxValue; }
    public void setMaxValue(BigDecimal maxValue) { this.maxValue = maxValue; }

    public Integer getSampleCount() { return sampleCount; }
    public void setSampleCount(Integer sampleCount) { this.sampleCount = sampleCount; }

}
