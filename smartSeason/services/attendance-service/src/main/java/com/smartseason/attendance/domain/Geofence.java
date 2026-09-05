package com.smartseason.attendance.domain;

import com.smartseason.attendance.platform.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "geofences", indexes = {
        @Index(name = "ix_geofences_farm_id", columnList = "farm_id"),
        @Index(name = "ix_geofences_plot_id", columnList = "plot_id")
})
public class Geofence extends BaseEntity {

    @Column(name = "farm_id", nullable = false)
    private UUID farmId;

    @Column(name = "plot_id")
    private UUID plotId;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "center_lat", nullable = false)
    private BigDecimal centerLat;

    @Column(name = "center_lng", nullable = false)
    private BigDecimal centerLng;

    @Column(name = "radius_m", nullable = false)
    private Integer radiusM;

    @Column(name = "active", nullable = false)
    private Boolean active;

    public UUID getFarmId() { return farmId; }
    public void setFarmId(UUID farmId) { this.farmId = farmId; }

    public UUID getPlotId() { return plotId; }
    public void setPlotId(UUID plotId) { this.plotId = plotId; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public BigDecimal getCenterLat() { return centerLat; }
    public void setCenterLat(BigDecimal centerLat) { this.centerLat = centerLat; }

    public BigDecimal getCenterLng() { return centerLng; }
    public void setCenterLng(BigDecimal centerLng) { this.centerLng = centerLng; }

    public Integer getRadiusM() { return radiusM; }
    public void setRadiusM(Integer radiusM) { this.radiusM = radiusM; }

    public Boolean getActive() { return active; }
    public void setActive(Boolean active) { this.active = active; }

}
