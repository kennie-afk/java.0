package com.smartseason.analytics.domain;

import com.smartseason.analytics.platform.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "metric_snapshots", indexes = {
        @Index(name = "ix_metric_snapshots_metric_key", columnList = "metric_key"),
        @Index(name = "ix_metric_snapshots_dimension", columnList = "dimension"),
        @Index(name = "ix_metric_snapshots_dimension_value", columnList = "dimension_value")
})
public class MetricSnapshot extends BaseEntity {

    @Column(name = "metric_key", nullable = false)
    private String metricKey;

    @Column(name = "dimension")
    private String dimension;

    @Column(name = "dimension_value")
    private String dimensionValue;

    @Column(name = "period_start", nullable = false)
    private Instant periodStart;

    @Column(name = "period_end", nullable = false)
    private Instant periodEnd;

    @Enumerated(EnumType.STRING)
    @Column(name = "granularity", nullable = false)
    private Granularity granularity;

    @Column(name = "value", nullable = false)
    private BigDecimal value;

    @Column(name = "unit")
    private String unit;

    @Column(name = "computed_at", nullable = false)
    private Instant computedAt;

    public String getMetricKey() { return metricKey; }
    public void setMetricKey(String metricKey) { this.metricKey = metricKey; }

    public String getDimension() { return dimension; }
    public void setDimension(String dimension) { this.dimension = dimension; }

    public String getDimensionValue() { return dimensionValue; }
    public void setDimensionValue(String dimensionValue) { this.dimensionValue = dimensionValue; }

    public Instant getPeriodStart() { return periodStart; }
    public void setPeriodStart(Instant periodStart) { this.periodStart = periodStart; }

    public Instant getPeriodEnd() { return periodEnd; }
    public void setPeriodEnd(Instant periodEnd) { this.periodEnd = periodEnd; }

    public Granularity getGranularity() { return granularity; }
    public void setGranularity(Granularity granularity) { this.granularity = granularity; }

    public BigDecimal getValue() { return value; }
    public void setValue(BigDecimal value) { this.value = value; }

    public String getUnit() { return unit; }
    public void setUnit(String unit) { this.unit = unit; }

    public Instant getComputedAt() { return computedAt; }
    public void setComputedAt(Instant computedAt) { this.computedAt = computedAt; }

    public enum Granularity { HOUR, DAY, WEEK, MONTH, SEASON }

}
