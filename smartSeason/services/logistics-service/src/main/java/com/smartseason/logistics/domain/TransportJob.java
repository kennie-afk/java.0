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
@Table(name = "transport_jobs", indexes = {
        @Index(name = "ix_transport_jobs_job_number", columnList = "job_number"),
        @Index(name = "ix_transport_jobs_order_id", columnList = "order_id"),
        @Index(name = "ix_transport_jobs_batch_id", columnList = "batch_id"),
        @Index(name = "ix_transport_jobs_vehicle_id", columnList = "vehicle_id"),
        @Index(name = "ix_transport_jobs_driver_id", columnList = "driver_id")
})
public class TransportJob extends BaseEntity {

    @Column(name = "job_number", nullable = false, unique = true)
    private String jobNumber;

    @Column(name = "order_id")
    private UUID orderId;

    @Column(name = "batch_id")
    private UUID batchId;

    @Column(name = "vehicle_id")
    private UUID vehicleId;

    @Column(name = "driver_id")
    private UUID driverId;

    @Column(name = "pickup_county")
    private String pickupCounty;

    @Column(name = "pickup_lat")
    private BigDecimal pickupLat;

    @Column(name = "pickup_lng")
    private BigDecimal pickupLng;

    @Column(name = "pickup_at")
    private Instant pickupAt;

    @Column(name = "dropoff_county")
    private String dropoffCounty;

    @Column(name = "dropoff_lat")
    private BigDecimal dropoffLat;

    @Column(name = "dropoff_lng")
    private BigDecimal dropoffLng;

    @Column(name = "dropoff_at")
    private Instant dropoffAt;

    @Column(name = "distance_km")
    private BigDecimal distanceKm;

    @Column(name = "weight_kg")
    private BigDecimal weightKg;

    @Column(name = "freight_cost")
    private BigDecimal freightCost;

    @Column(name = "currency")
    private String currency;

    @Column(name = "requires_cold_chain", nullable = false)
    private Boolean requiresColdChain;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private Status status;

    public String getJobNumber() { return jobNumber; }
    public void setJobNumber(String jobNumber) { this.jobNumber = jobNumber; }

    public UUID getOrderId() { return orderId; }
    public void setOrderId(UUID orderId) { this.orderId = orderId; }

    public UUID getBatchId() { return batchId; }
    public void setBatchId(UUID batchId) { this.batchId = batchId; }

    public UUID getVehicleId() { return vehicleId; }
    public void setVehicleId(UUID vehicleId) { this.vehicleId = vehicleId; }

    public UUID getDriverId() { return driverId; }
    public void setDriverId(UUID driverId) { this.driverId = driverId; }

    public String getPickupCounty() { return pickupCounty; }
    public void setPickupCounty(String pickupCounty) { this.pickupCounty = pickupCounty; }

    public BigDecimal getPickupLat() { return pickupLat; }
    public void setPickupLat(BigDecimal pickupLat) { this.pickupLat = pickupLat; }

    public BigDecimal getPickupLng() { return pickupLng; }
    public void setPickupLng(BigDecimal pickupLng) { this.pickupLng = pickupLng; }

    public Instant getPickupAt() { return pickupAt; }
    public void setPickupAt(Instant pickupAt) { this.pickupAt = pickupAt; }

    public String getDropoffCounty() { return dropoffCounty; }
    public void setDropoffCounty(String dropoffCounty) { this.dropoffCounty = dropoffCounty; }

    public BigDecimal getDropoffLat() { return dropoffLat; }
    public void setDropoffLat(BigDecimal dropoffLat) { this.dropoffLat = dropoffLat; }

    public BigDecimal getDropoffLng() { return dropoffLng; }
    public void setDropoffLng(BigDecimal dropoffLng) { this.dropoffLng = dropoffLng; }

    public Instant getDropoffAt() { return dropoffAt; }
    public void setDropoffAt(Instant dropoffAt) { this.dropoffAt = dropoffAt; }

    public BigDecimal getDistanceKm() { return distanceKm; }
    public void setDistanceKm(BigDecimal distanceKm) { this.distanceKm = distanceKm; }

    public BigDecimal getWeightKg() { return weightKg; }
    public void setWeightKg(BigDecimal weightKg) { this.weightKg = weightKg; }

    public BigDecimal getFreightCost() { return freightCost; }
    public void setFreightCost(BigDecimal freightCost) { this.freightCost = freightCost; }

    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }

    public Boolean getRequiresColdChain() { return requiresColdChain; }
    public void setRequiresColdChain(Boolean requiresColdChain) { this.requiresColdChain = requiresColdChain; }

    public Status getStatus() { return status; }
    public void setStatus(Status status) { this.status = status; }

    public enum Status { CREATED, ASSIGNED, PICKED_UP, IN_TRANSIT, DELIVERED, FAILED, CANCELLED }

}
