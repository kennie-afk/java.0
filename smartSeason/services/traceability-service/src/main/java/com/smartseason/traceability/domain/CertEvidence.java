package com.smartseason.traceability.domain;

import com.smartseason.traceability.platform.BaseEntity;
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
@Table(name = "cert_evidence", indexes = {
        @Index(name = "ix_cert_evidence_batch_code", columnList = "batch_code"),
        @Index(name = "ix_cert_evidence_farm_id", columnList = "farm_id")
})
public class CertEvidence extends BaseEntity {

    @Column(name = "batch_code")
    private String batchCode;

    @Column(name = "farm_id")
    private UUID farmId;

    @Column(name = "certification_code", nullable = false)
    private String certificationCode;

    @Column(name = "certificate_no")
    private String certificateNo;

    @Column(name = "issued_by")
    private String issuedBy;

    @Column(name = "issued_on")
    private LocalDate issuedOn;

    @Column(name = "expires_on")
    private LocalDate expiresOn;

    @Column(name = "document_url")
    private String documentUrl;

    @Column(name = "verified", nullable = false)
    private Boolean verified;

    @Column(name = "verified_at")
    private Instant verifiedAt;

    public String getBatchCode() { return batchCode; }
    public void setBatchCode(String batchCode) { this.batchCode = batchCode; }

    public UUID getFarmId() { return farmId; }
    public void setFarmId(UUID farmId) { this.farmId = farmId; }

    public String getCertificationCode() { return certificationCode; }
    public void setCertificationCode(String certificationCode) { this.certificationCode = certificationCode; }

    public String getCertificateNo() { return certificateNo; }
    public void setCertificateNo(String certificateNo) { this.certificateNo = certificateNo; }

    public String getIssuedBy() { return issuedBy; }
    public void setIssuedBy(String issuedBy) { this.issuedBy = issuedBy; }

    public LocalDate getIssuedOn() { return issuedOn; }
    public void setIssuedOn(LocalDate issuedOn) { this.issuedOn = issuedOn; }

    public LocalDate getExpiresOn() { return expiresOn; }
    public void setExpiresOn(LocalDate expiresOn) { this.expiresOn = expiresOn; }

    public String getDocumentUrl() { return documentUrl; }
    public void setDocumentUrl(String documentUrl) { this.documentUrl = documentUrl; }

    public Boolean getVerified() { return verified; }
    public void setVerified(Boolean verified) { this.verified = verified; }

    public Instant getVerifiedAt() { return verifiedAt; }
    public void setVerifiedAt(Instant verifiedAt) { this.verifiedAt = verifiedAt; }

}
