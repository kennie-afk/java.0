package com.smartseason.weather.domain;

import com.smartseason.weather.platform.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.math.BigDecimal;

@Entity
@Table(name = "weather_stations", indexes = {
        @Index(name = "ix_weather_stations_external_id", columnList = "external_id"),
        @Index(name = "ix_weather_stations_county", columnList = "county")
})
public class WeatherStation extends BaseEntity {

    @Column(name = "external_id", nullable = false, unique = true)
    private String externalId;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "latitude", nullable = false)
    private BigDecimal latitude;

    @Column(name = "longitude", nullable = false)
    private BigDecimal longitude;

    @Column(name = "elevation_m")
    private BigDecimal elevationM;

    @Column(name = "provider", nullable = false)
    private String provider;

    @Column(name = "county")
    private String county;

    @Column(name = "active", nullable = false)
    private Boolean active;

    public String getExternalId() { return externalId; }
    public void setExternalId(String externalId) { this.externalId = externalId; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public BigDecimal getLatitude() { return latitude; }
    public void setLatitude(BigDecimal latitude) { this.latitude = latitude; }

    public BigDecimal getLongitude() { return longitude; }
    public void setLongitude(BigDecimal longitude) { this.longitude = longitude; }

    public BigDecimal getElevationM() { return elevationM; }
    public void setElevationM(BigDecimal elevationM) { this.elevationM = elevationM; }

    public String getProvider() { return provider; }
    public void setProvider(String provider) { this.provider = provider; }

    public String getCounty() { return county; }
    public void setCounty(String county) { this.county = county; }

    public Boolean getActive() { return active; }
    public void setActive(Boolean active) { this.active = active; }

}
