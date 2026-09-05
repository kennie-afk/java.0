package com.smartseason.farm.domain;

import com.smartseason.farm.platform.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "plots", indexes = {
        @Index(name = "ix_plots_farm_id", columnList = "farm_id")
})
public class Plot extends BaseEntity {

    @Column(name = "farm_id", nullable = false)
    private UUID farmId;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "area_ha", nullable = false)
    private BigDecimal areaHa;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "boundary_geojson", columnDefinition = "jsonb")
    private String boundaryGeojson;

    @Column(name = "centroid_lat")
    private BigDecimal centroidLat;

    @Column(name = "centroid_lng")
    private BigDecimal centroidLng;

    @Column(name = "irrigated", nullable = false)
    private Boolean irrigated;

    @Column(name = "current_crop")
    private String currentCrop;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private Status status;

    public UUID getFarmId() { return farmId; }
    public void setFarmId(UUID farmId) { this.farmId = farmId; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public BigDecimal getAreaHa() { return areaHa; }
    public void setAreaHa(BigDecimal areaHa) { this.areaHa = areaHa; }

    public String getBoundaryGeojson() { return boundaryGeojson; }
    public void setBoundaryGeojson(String boundaryGeojson) { this.boundaryGeojson = boundaryGeojson; }

    public BigDecimal getCentroidLat() { return centroidLat; }
    public void setCentroidLat(BigDecimal centroidLat) { this.centroidLat = centroidLat; }

    public BigDecimal getCentroidLng() { return centroidLng; }
    public void setCentroidLng(BigDecimal centroidLng) { this.centroidLng = centroidLng; }

    public Boolean getIrrigated() { return irrigated; }
    public void setIrrigated(Boolean irrigated) { this.irrigated = irrigated; }

    public String getCurrentCrop() { return currentCrop; }
    public void setCurrentCrop(String currentCrop) { this.currentCrop = currentCrop; }

    public Status getStatus() { return status; }
    public void setStatus(Status status) { this.status = status; }

    public enum Status { ACTIVE, FALLOW, RETIRED }

}
