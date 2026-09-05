package com.smartseason.inventory.domain;

import com.smartseason.inventory.platform.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "warehouses", indexes = {
        @Index(name = "ix_warehouses_county", columnList = "county")
})
public class Warehouse extends BaseEntity {

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "county")
    private String county;

    @Column(name = "latitude")
    private BigDecimal latitude;

    @Column(name = "longitude")
    private BigDecimal longitude;

    @Column(name = "capacity_kg")
    private BigDecimal capacityKg;

    @Column(name = "cold_chain", nullable = false)
    private Boolean coldChain;

    @Column(name = "manager_user_id")
    private UUID managerUserId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private Status status;

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getCounty() { return county; }
    public void setCounty(String county) { this.county = county; }

    public BigDecimal getLatitude() { return latitude; }
    public void setLatitude(BigDecimal latitude) { this.latitude = latitude; }

    public BigDecimal getLongitude() { return longitude; }
    public void setLongitude(BigDecimal longitude) { this.longitude = longitude; }

    public BigDecimal getCapacityKg() { return capacityKg; }
    public void setCapacityKg(BigDecimal capacityKg) { this.capacityKg = capacityKg; }

    public Boolean getColdChain() { return coldChain; }
    public void setColdChain(Boolean coldChain) { this.coldChain = coldChain; }

    public UUID getManagerUserId() { return managerUserId; }
    public void setManagerUserId(UUID managerUserId) { this.managerUserId = managerUserId; }

    public Status getStatus() { return status; }
    public void setStatus(Status status) { this.status = status; }

    public enum Status { ACTIVE, CLOSED }

}
