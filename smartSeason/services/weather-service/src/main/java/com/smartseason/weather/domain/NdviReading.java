package com.smartseason.weather.domain;

import com.smartseason.weather.platform.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "ndvi_readings", indexes = {
        @Index(name = "ix_ndvi_readings_plot_id", columnList = "plot_id"),
        @Index(name = "ix_ndvi_readings_geo_cell", columnList = "geo_cell")
})
public class NdviReading extends BaseEntity {

    @Column(name = "plot_id")
    private UUID plotId;

    @Column(name = "geo_cell")
    private String geoCell;

    @Column(name = "captured_on", nullable = false)
    private LocalDate capturedOn;

    @Column(name = "ndvi", nullable = false)
    private BigDecimal ndvi;

    @Column(name = "cloud_cover_pct")
    private BigDecimal cloudCoverPct;

    @Column(name = "satellite")
    private String satellite;

    @Column(name = "tile_url")
    private String tileUrl;

    public UUID getPlotId() { return plotId; }
    public void setPlotId(UUID plotId) { this.plotId = plotId; }

    public String getGeoCell() { return geoCell; }
    public void setGeoCell(String geoCell) { this.geoCell = geoCell; }

    public LocalDate getCapturedOn() { return capturedOn; }
    public void setCapturedOn(LocalDate capturedOn) { this.capturedOn = capturedOn; }

    public BigDecimal getNdvi() { return ndvi; }
    public void setNdvi(BigDecimal ndvi) { this.ndvi = ndvi; }

    public BigDecimal getCloudCoverPct() { return cloudCoverPct; }
    public void setCloudCoverPct(BigDecimal cloudCoverPct) { this.cloudCoverPct = cloudCoverPct; }

    public String getSatellite() { return satellite; }
    public void setSatellite(String satellite) { this.satellite = satellite; }

    public String getTileUrl() { return tileUrl; }
    public void setTileUrl(String tileUrl) { this.tileUrl = tileUrl; }

}
