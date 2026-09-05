package com.smartseason.traceability.domain;

import com.smartseason.traceability.platform.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.Instant;

@Entity
@Table(name = "qr_passes", indexes = {
        @Index(name = "ix_qr_passes_batch_code", columnList = "batch_code"),
        @Index(name = "ix_qr_passes_pass_code", columnList = "pass_code")
})
public class QrPass extends BaseEntity {

    @Column(name = "batch_code", nullable = false)
    private String batchCode;

    @Column(name = "pass_code", nullable = false, unique = true)
    private String passCode;

    @Column(name = "qr_url")
    private String qrUrl;

    @Column(name = "issued_at", nullable = false)
    private Instant issuedAt;

    @Column(name = "expires_at")
    private Instant expiresAt;

    @Column(name = "scan_count", nullable = false)
    private Integer scanCount;

    @Column(name = "last_scanned_at")
    private Instant lastScannedAt;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "public_summary", columnDefinition = "jsonb")
    private String publicSummary;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private Status status;

    public String getBatchCode() { return batchCode; }
    public void setBatchCode(String batchCode) { this.batchCode = batchCode; }

    public String getPassCode() { return passCode; }
    public void setPassCode(String passCode) { this.passCode = passCode; }

    public String getQrUrl() { return qrUrl; }
    public void setQrUrl(String qrUrl) { this.qrUrl = qrUrl; }

    public Instant getIssuedAt() { return issuedAt; }
    public void setIssuedAt(Instant issuedAt) { this.issuedAt = issuedAt; }

    public Instant getExpiresAt() { return expiresAt; }
    public void setExpiresAt(Instant expiresAt) { this.expiresAt = expiresAt; }

    public Integer getScanCount() { return scanCount; }
    public void setScanCount(Integer scanCount) { this.scanCount = scanCount; }

    public Instant getLastScannedAt() { return lastScannedAt; }
    public void setLastScannedAt(Instant lastScannedAt) { this.lastScannedAt = lastScannedAt; }

    public String getPublicSummary() { return publicSummary; }
    public void setPublicSummary(String publicSummary) { this.publicSummary = publicSummary; }

    public Status getStatus() { return status; }
    public void setStatus(Status status) { this.status = status; }

    public enum Status { ACTIVE, REVOKED, EXPIRED }

}
