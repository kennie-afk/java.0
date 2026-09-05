package com.smartseason.pricing.domain;

import com.smartseason.pricing.platform.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "market_indices", indexes = {
        @Index(name = "ix_market_indices_commodity_code", columnList = "commodity_code"),
        @Index(name = "ix_market_indices_region", columnList = "region")
})
public class MarketIndex extends BaseEntity {

    @Column(name = "commodity_code", nullable = false)
    private String commodityCode;

    @Column(name = "region", nullable = false)
    private String region;

    @Column(name = "period_start", nullable = false)
    private LocalDate periodStart;

    @Column(name = "period_end", nullable = false)
    private LocalDate periodEnd;

    @Column(name = "index_value", nullable = false)
    private BigDecimal indexValue;

    @Column(name = "change_pct")
    private BigDecimal changePct;

    @Column(name = "basis")
    private String basis;

    @Column(name = "computed_at", nullable = false)
    private Instant computedAt;

    public String getCommodityCode() { return commodityCode; }
    public void setCommodityCode(String commodityCode) { this.commodityCode = commodityCode; }

    public String getRegion() { return region; }
    public void setRegion(String region) { this.region = region; }

    public LocalDate getPeriodStart() { return periodStart; }
    public void setPeriodStart(LocalDate periodStart) { this.periodStart = periodStart; }

    public LocalDate getPeriodEnd() { return periodEnd; }
    public void setPeriodEnd(LocalDate periodEnd) { this.periodEnd = periodEnd; }

    public BigDecimal getIndexValue() { return indexValue; }
    public void setIndexValue(BigDecimal indexValue) { this.indexValue = indexValue; }

    public BigDecimal getChangePct() { return changePct; }
    public void setChangePct(BigDecimal changePct) { this.changePct = changePct; }

    public String getBasis() { return basis; }
    public void setBasis(String basis) { this.basis = basis; }

    public Instant getComputedAt() { return computedAt; }
    public void setComputedAt(Instant computedAt) { this.computedAt = computedAt; }

}
