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
@Table(name = "route_stops", indexes = {
        @Index(name = "ix_route_stops_transport_job_id", columnList = "transport_job_id")
})
public class RouteStop extends BaseEntity {

    @Column(name = "transport_job_id", nullable = false)
    private UUID transportJobId;

    @Column(name = "sequence", nullable = false)
    private Integer sequence;

    @Enumerated(EnumType.STRING)
    @Column(name = "stop_type", nullable = false)
    private StopType stopType;

    @Column(name = "latitude")
    private BigDecimal latitude;

    @Column(name = "longitude")
    private BigDecimal longitude;

    @Column(name = "planned_at")
    private Instant plannedAt;

    @Column(name = "arrived_at")
    private Instant arrivedAt;

    @Column(name = "departed_at")
    private Instant departedAt;

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    @Column(name = "off_route", nullable = false)
    private Boolean offRoute;

    public UUID getTransportJobId() { return transportJobId; }
    public void setTransportJobId(UUID transportJobId) { this.transportJobId = transportJobId; }

    public Integer getSequence() { return sequence; }
    public void setSequence(Integer sequence) { this.sequence = sequence; }

    public StopType getStopType() { return stopType; }
    public void setStopType(StopType stopType) { this.stopType = stopType; }

    public BigDecimal getLatitude() { return latitude; }
    public void setLatitude(BigDecimal latitude) { this.latitude = latitude; }

    public BigDecimal getLongitude() { return longitude; }
    public void setLongitude(BigDecimal longitude) { this.longitude = longitude; }

    public Instant getPlannedAt() { return plannedAt; }
    public void setPlannedAt(Instant plannedAt) { this.plannedAt = plannedAt; }

    public Instant getArrivedAt() { return arrivedAt; }
    public void setArrivedAt(Instant arrivedAt) { this.arrivedAt = arrivedAt; }

    public Instant getDepartedAt() { return departedAt; }
    public void setDepartedAt(Instant departedAt) { this.departedAt = departedAt; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }

    public Boolean getOffRoute() { return offRoute; }
    public void setOffRoute(Boolean offRoute) { this.offRoute = offRoute; }

    public enum StopType { PICKUP, DROPOFF, WAYPOINT, CHECKPOINT }

}
