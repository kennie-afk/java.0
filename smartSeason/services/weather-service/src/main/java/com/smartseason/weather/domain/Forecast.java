package com.smartseason.weather.domain;

import com.smartseason.weather.platform.BaseEntity;
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
@Table(name = "forecasts", indexes = {
        @Index(name = "ix_forecasts_geo_cell", columnList = "geo_cell")
})
public class Forecast extends BaseEntity {

    @Column(name = "geo_cell", nullable = false)
    private String geoCell;

    @Column(name = "forecast_for", nullable = false)
    private LocalDate forecastFor;

    @Column(name = "issued_at", nullable = false)
    private Instant issuedAt;

    @Column(name = "temp_min_c")
    private BigDecimal tempMinC;

    @Column(name = "temp_max_c")
    private BigDecimal tempMaxC;

    @Column(name = "rainfall_mm")
    private BigDecimal rainfallMm;

    @Column(name = "humidity_pct")
    private BigDecimal humidityPct;

    @Column(name = "wind_kph")
    private BigDecimal windKph;

    @Column(name = "conditions")
    private String conditions;

    @Column(name = "provider")
    private String provider;

    public String getGeoCell() { return geoCell; }
    public void setGeoCell(String geoCell) { this.geoCell = geoCell; }

    public LocalDate getForecastFor() { return forecastFor; }
    public void setForecastFor(LocalDate forecastFor) { this.forecastFor = forecastFor; }

    public Instant getIssuedAt() { return issuedAt; }
    public void setIssuedAt(Instant issuedAt) { this.issuedAt = issuedAt; }

    public BigDecimal getTempMinC() { return tempMinC; }
    public void setTempMinC(BigDecimal tempMinC) { this.tempMinC = tempMinC; }

    public BigDecimal getTempMaxC() { return tempMaxC; }
    public void setTempMaxC(BigDecimal tempMaxC) { this.tempMaxC = tempMaxC; }

    public BigDecimal getRainfallMm() { return rainfallMm; }
    public void setRainfallMm(BigDecimal rainfallMm) { this.rainfallMm = rainfallMm; }

    public BigDecimal getHumidityPct() { return humidityPct; }
    public void setHumidityPct(BigDecimal humidityPct) { this.humidityPct = humidityPct; }

    public BigDecimal getWindKph() { return windKph; }
    public void setWindKph(BigDecimal windKph) { this.windKph = windKph; }

    public String getConditions() { return conditions; }
    public void setConditions(String conditions) { this.conditions = conditions; }

    public String getProvider() { return provider; }
    public void setProvider(String provider) { this.provider = provider; }

}
