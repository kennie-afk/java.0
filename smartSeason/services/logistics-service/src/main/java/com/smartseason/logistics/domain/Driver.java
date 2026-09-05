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
@Table(name = "drivers", indexes = {
        @Index(name = "ix_drivers_user_id", columnList = "user_id"),
        @Index(name = "ix_drivers_phone", columnList = "phone"),
        @Index(name = "ix_drivers_licence_number", columnList = "licence_number"),
        @Index(name = "ix_drivers_assigned_vehicle_id", columnList = "assigned_vehicle_id")
})
public class Driver extends BaseEntity {

    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "full_name", nullable = false)
    private String fullName;

    @Column(name = "phone", nullable = false)
    private String phone;

    @Column(name = "licence_number", unique = true)
    private String licenceNumber;

    @Column(name = "licence_expiry")
    private LocalDate licenceExpiry;

    @Column(name = "assigned_vehicle_id")
    private UUID assignedVehicleId;

    @Column(name = "rating")
    private BigDecimal rating;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private Status status;

    public UUID getUserId() { return userId; }
    public void setUserId(UUID userId) { this.userId = userId; }

    public String getFullName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = fullName; }

    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }

    public String getLicenceNumber() { return licenceNumber; }
    public void setLicenceNumber(String licenceNumber) { this.licenceNumber = licenceNumber; }

    public LocalDate getLicenceExpiry() { return licenceExpiry; }
    public void setLicenceExpiry(LocalDate licenceExpiry) { this.licenceExpiry = licenceExpiry; }

    public UUID getAssignedVehicleId() { return assignedVehicleId; }
    public void setAssignedVehicleId(UUID assignedVehicleId) { this.assignedVehicleId = assignedVehicleId; }

    public BigDecimal getRating() { return rating; }
    public void setRating(BigDecimal rating) { this.rating = rating; }

    public Status getStatus() { return status; }
    public void setStatus(Status status) { this.status = status; }

    public enum Status { AVAILABLE, ON_JOB, SUSPENDED, OFFLINE }

}
