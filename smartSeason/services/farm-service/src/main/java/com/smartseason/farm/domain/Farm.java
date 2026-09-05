package com.smartseason.farm.domain;

import com.smartseason.farm.platform.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "farms", indexes = {
        @Index(name = "ix_farms_owner_user_id", columnList = "owner_user_id"),
        @Index(name = "ix_farms_county", columnList = "county"),
        @Index(name = "ix_farms_cooperative_id", columnList = "cooperative_id")
})
public class Farm extends BaseEntity {

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "owner_user_id")
    private UUID ownerUserId;

    @Column(name = "county")
    private String county;

    @Column(name = "sub_county")
    private String subCounty;

    @Column(name = "ward")
    private String ward;

    @Column(name = "latitude")
    private BigDecimal latitude;

    @Column(name = "longitude")
    private BigDecimal longitude;

    @Column(name = "total_area_ha")
    private BigDecimal totalAreaHa;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private Status status;

    @Column(name = "cooperative_id")
    private UUID cooperativeId;

    @Column(name = "registration_no")
    private String registrationNo;

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public UUID getOwnerUserId() { return ownerUserId; }
    public void setOwnerUserId(UUID ownerUserId) { this.ownerUserId = ownerUserId; }

    public String getCounty() { return county; }
    public void setCounty(String county) { this.county = county; }

    public String getSubCounty() { return subCounty; }
    public void setSubCounty(String subCounty) { this.subCounty = subCounty; }

    public String getWard() { return ward; }
    public void setWard(String ward) { this.ward = ward; }

    public BigDecimal getLatitude() { return latitude; }
    public void setLatitude(BigDecimal latitude) { this.latitude = latitude; }

    public BigDecimal getLongitude() { return longitude; }
    public void setLongitude(BigDecimal longitude) { this.longitude = longitude; }

    public BigDecimal getTotalAreaHa() { return totalAreaHa; }
    public void setTotalAreaHa(BigDecimal totalAreaHa) { this.totalAreaHa = totalAreaHa; }

    public Status getStatus() { return status; }
    public void setStatus(Status status) { this.status = status; }

    public UUID getCooperativeId() { return cooperativeId; }
    public void setCooperativeId(UUID cooperativeId) { this.cooperativeId = cooperativeId; }

    public String getRegistrationNo() { return registrationNo; }
    public void setRegistrationNo(String registrationNo) { this.registrationNo = registrationNo; }

    public enum Status { ACTIVE, ARCHIVED }

}
