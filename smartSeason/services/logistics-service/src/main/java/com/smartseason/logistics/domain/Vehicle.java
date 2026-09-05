package com.smartseason.logistics.domain;

import com.smartseason.logistics.platform.BaseEntity;
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
@Table(name = "vehicles", indexes = {
        @Index(name = "ix_vehicles_registration_no", columnList = "registration_no"),
        @Index(name = "ix_vehicles_owner_org_id", columnList = "owner_org_id")
})
public class Vehicle extends BaseEntity {

    @Column(name = "registration_no", nullable = false, unique = true)
    private String registrationNo;

    @Enumerated(EnumType.STRING)
    @Column(name = "vehicle_type", nullable = false)
    private VehicleType vehicleType;

    @Column(name = "capacity_kg")
    private BigDecimal capacityKg;

    @Column(name = "cold_chain", nullable = false)
    private Boolean coldChain;

    @Column(name = "owner_org_id")
    private UUID ownerOrgId;

    @Column(name = "odometer_km")
    private BigDecimal odometerKm;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private Status status;

    @Column(name = "last_service_at")
    private LocalDate lastServiceAt;

    public String getRegistrationNo() { return registrationNo; }
    public void setRegistrationNo(String registrationNo) { this.registrationNo = registrationNo; }

    public VehicleType getVehicleType() { return vehicleType; }
    public void setVehicleType(VehicleType vehicleType) { this.vehicleType = vehicleType; }

    public BigDecimal getCapacityKg() { return capacityKg; }
    public void setCapacityKg(BigDecimal capacityKg) { this.capacityKg = capacityKg; }

    public Boolean getColdChain() { return coldChain; }
    public void setColdChain(Boolean coldChain) { this.coldChain = coldChain; }

    public UUID getOwnerOrgId() { return ownerOrgId; }
    public void setOwnerOrgId(UUID ownerOrgId) { this.ownerOrgId = ownerOrgId; }

    public BigDecimal getOdometerKm() { return odometerKm; }
    public void setOdometerKm(BigDecimal odometerKm) { this.odometerKm = odometerKm; }

    public Status getStatus() { return status; }
    public void setStatus(Status status) { this.status = status; }

    public LocalDate getLastServiceAt() { return lastServiceAt; }
    public void setLastServiceAt(LocalDate lastServiceAt) { this.lastServiceAt = lastServiceAt; }

    public enum VehicleType { PICKUP, TRUCK, REFRIGERATED, MOTORCYCLE, TRACTOR }

    public enum Status { AVAILABLE, ON_JOB, MAINTENANCE, RETIRED }

}
