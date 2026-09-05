package com.smartseason.identity.domain;

import com.smartseason.identity.platform.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

@Entity
@Table(name = "organisations", indexes = {
        @Index(name = "ix_organisations_registration_no", columnList = "registration_no")
})
public class Organisation extends BaseEntity {

    @Column(name = "name", nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "org_type", nullable = false)
    private OrgType orgType;

    @Column(name = "county")
    private String county;

    @Column(name = "registration_no", unique = true)
    private String registrationNo;

    @Column(name = "phone")
    private String phone;

    @Column(name = "email")
    private String email;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private Status status;

    @Enumerated(EnumType.STRING)
    @Column(name = "kyc_status", nullable = false)
    private KycStatus kycStatus;

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public OrgType getOrgType() { return orgType; }
    public void setOrgType(OrgType orgType) { this.orgType = orgType; }

    public String getCounty() { return county; }
    public void setCounty(String county) { this.county = county; }

    public String getRegistrationNo() { return registrationNo; }
    public void setRegistrationNo(String registrationNo) { this.registrationNo = registrationNo; }

    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public Status getStatus() { return status; }
    public void setStatus(Status status) { this.status = status; }

    public KycStatus getKycStatus() { return kycStatus; }
    public void setKycStatus(KycStatus kycStatus) { this.kycStatus = kycStatus; }

    public enum OrgType { FARM, COOPERATIVE, BUYER, TRANSPORTER, ADMIN }

    public enum Status { ACTIVE, SUSPENDED, PENDING }

    public enum KycStatus { NONE, PENDING, VERIFIED, REJECTED }

}
