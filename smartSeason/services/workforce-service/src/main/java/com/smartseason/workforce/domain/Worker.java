package com.smartseason.workforce.domain;

import com.smartseason.workforce.platform.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "workers", indexes = {
        @Index(name = "ix_workers_national_id", columnList = "national_id"),
        @Index(name = "ix_workers_phone", columnList = "phone"),
        @Index(name = "ix_workers_farm_id", columnList = "farm_id")
})
public class Worker extends BaseEntity {

    @Column(name = "national_id")
    private String nationalId;

    @Column(name = "full_name", nullable = false)
    private String fullName;

    @Column(name = "phone")
    private String phone;

    @Enumerated(EnumType.STRING)
    @Column(name = "gender")
    private Gender gender;

    @Column(name = "date_of_birth")
    private LocalDate dateOfBirth;

    @Column(name = "farm_id")
    private UUID farmId;

    @Column(name = "payout_phone")
    private String payoutPhone;

    @Column(name = "payout_account")
    private String payoutAccount;

    @Column(name = "biometric_ref")
    private String biometricRef;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private Status status;

    @Column(name = "risk_score", nullable = false)
    private Integer riskScore;

    @Column(name = "onboarded_at")
    private Instant onboardedAt;

    @Column(name = "photo_url")
    private String photoUrl;

    public String getNationalId() { return nationalId; }
    public void setNationalId(String nationalId) { this.nationalId = nationalId; }

    public String getFullName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = fullName; }

    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }

    public Gender getGender() { return gender; }
    public void setGender(Gender gender) { this.gender = gender; }

    public LocalDate getDateOfBirth() { return dateOfBirth; }
    public void setDateOfBirth(LocalDate dateOfBirth) { this.dateOfBirth = dateOfBirth; }

    public UUID getFarmId() { return farmId; }
    public void setFarmId(UUID farmId) { this.farmId = farmId; }

    public String getPayoutPhone() { return payoutPhone; }
    public void setPayoutPhone(String payoutPhone) { this.payoutPhone = payoutPhone; }

    public String getPayoutAccount() { return payoutAccount; }
    public void setPayoutAccount(String payoutAccount) { this.payoutAccount = payoutAccount; }

    public String getBiometricRef() { return biometricRef; }
    public void setBiometricRef(String biometricRef) { this.biometricRef = biometricRef; }

    public Status getStatus() { return status; }
    public void setStatus(Status status) { this.status = status; }

    public Integer getRiskScore() { return riskScore; }
    public void setRiskScore(Integer riskScore) { this.riskScore = riskScore; }

    public Instant getOnboardedAt() { return onboardedAt; }
    public void setOnboardedAt(Instant onboardedAt) { this.onboardedAt = onboardedAt; }

    public String getPhotoUrl() { return photoUrl; }
    public void setPhotoUrl(String photoUrl) { this.photoUrl = photoUrl; }

    public enum Gender { MALE, FEMALE, OTHER }

    public enum Status { ACTIVE, SUSPENDED, TERMINATED }

}
