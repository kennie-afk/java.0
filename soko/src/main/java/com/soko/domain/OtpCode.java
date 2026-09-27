package com.soko.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "otp_codes")
public class OtpCode {

    @Id @GeneratedValue private UUID id;

    @Column(name = "tenant_id", nullable = false) private UUID tenantId;
    @Column(nullable = false) private String phone;
    @Column(name = "code_hash", nullable = false) private String codeHash;
    @Column(name = "expires_at", nullable = false) private Instant expiresAt;
    @Column(nullable = false) private int attempts;
    @Column(name = "consumed_at") private Instant consumedAt;
    @Column(name = "created_at", nullable = false) private Instant createdAt = Instant.now();

    public UUID getId() { return id; }
    public void setId(UUID v) { this.id = v; }
    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID v) { this.tenantId = v; }
    public String getPhone() { return phone; }
    public void setPhone(String v) { this.phone = v; }
    public String getCodeHash() { return codeHash; }
    public void setCodeHash(String v) { this.codeHash = v; }
    public Instant getExpiresAt() { return expiresAt; }
    public void setExpiresAt(Instant v) { this.expiresAt = v; }
    public int getAttempts() { return attempts; }
    public void setAttempts(int v) { this.attempts = v; }
    public Instant getConsumedAt() { return consumedAt; }
    public void setConsumedAt(Instant v) { this.consumedAt = v; }
    public Instant getCreatedAt() { return createdAt; }
}
