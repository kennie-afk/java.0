package com.soko.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "users")
public class AppUser {

    @Id @GeneratedValue private UUID id;

    @Column(name = "tenant_id", nullable = false) private UUID tenantId;
    @Column(nullable = false) private String email;
    @Column(name = "full_name", nullable = false) private String fullName;
    @Column(name = "password_hash", nullable = false) private String passwordHash;
    @Column(nullable = false) private String role = "OPERATOR";
    @Column(nullable = false) private String status = "ACTIVE";
    @Column(name = "created_at", nullable = false) private Instant createdAt = Instant.now();

    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID v) { this.tenantId = v; }
    public String getEmail() { return email; }
    public void setEmail(String v) { this.email = v; }
    public String getFullName() { return fullName; }
    public void setFullName(String v) { this.fullName = v; }
    public String getPasswordHash() { return passwordHash; }
    public void setPasswordHash(String v) { this.passwordHash = v; }
    public String getRole() { return role; }
    public void setRole(String v) { this.role = v; }
    public String getStatus() { return status; }
    public void setStatus(String v) { this.status = v; }

    public UUID getId() { return id; }
    public void setId(UUID v) { this.id = v; }
}
