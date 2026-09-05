package com.smartseason.pricing.domain;

import com.smartseason.pricing.platform.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "price_series", indexes = {
        @Index(name = "ix_price_series_commodity_code", columnList = "commodity_code"),
        @Index(name = "ix_price_series_county", columnList = "county")
})
public class PriceSeries extends BaseEntity {

    @Column(name = "commodity_code", nullable = false)
    private String commodityCode;

    @Column(name = "county")
    private String county;

    @Column(name = "market_name")
    private String marketName;

    @Column(name = "grade")
    private String grade;

    @Column(name = "observed_on", nullable = false)
    private LocalDate observedOn;

    @Column(name = "unit", nullable = false)
    private String unit;

    @Column(name = "min_price")
    private BigDecimal minPrice;

    @Column(name = "max_price")
    private BigDecimal maxPrice;

    @Column(name = "avg_price", nullable = false)
    private BigDecimal avgPrice;

    @Column(name = "currency", nullable = false)
    private String currency;

    @Column(name = "source")
    private String source;

    @Column(name = "volume_kg")
    private BigDecimal volumeKg;

    public String getCommodityCode() { return commodityCode; }
    public void setCommodityCode(String commodityCode) { this.commodityCode = commodityCode; }

    public String getCounty() { return county; }
    public void setCounty(String county) { this.county = county; }

    public String getMarketName() { return marketName; }
    public void setMarketName(String marketName) { this.marketName = marketName; }

    public String getGrade() { return grade; }
    public void setGrade(String grade) { this.grade = grade; }

    public LocalDate getObservedOn() { return observedOn; }
    public void setObservedOn(LocalDate observedOn) { this.observedOn = observedOn; }

    public String getUnit() { return unit; }
    public void setUnit(String unit) { this.unit = unit; }

    public BigDecimal getMinPrice() { return minPrice; }
    public void setMinPrice(BigDecimal minPrice) { this.minPrice = minPrice; }

    public BigDecimal getMaxPrice() { return maxPrice; }
    public void setMaxPrice(BigDecimal maxPrice) { this.maxPrice = maxPrice; }

    public BigDecimal getAvgPrice() { return avgPrice; }
    public void setAvgPrice(BigDecimal avgPrice) { this.avgPrice = avgPrice; }

    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }

    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }

    public BigDecimal getVolumeKg() { return volumeKg; }
    public void setVolumeKg(BigDecimal volumeKg) { this.volumeKg = volumeKg; }

}
