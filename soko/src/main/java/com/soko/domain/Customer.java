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
@Table(name = "customers")
public class Customer {

    @Id @GeneratedValue private UUID id;

    @Column(name = "tenant_id", nullable = false) private UUID tenantId;
    @Column(nullable = false) private String name;
    @Column(nullable = false) private String phone;
    @Column(nullable = false) private String county;
    @Column(name = "created_at", nullable = false) private Instant createdAt = Instant.now();

    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID v) { this.tenantId = v; }
    public String getName() { return name; }
    public void setName(String v) { this.name = v; }
    public String getPhone() { return phone; }
    public void setPhone(String v) { this.phone = v; }
    public String getCounty() { return county; }
    public void setCounty(String v) { this.county = v; }

    public UUID getId() { return id; }
    public void setId(UUID v) { this.id = v; }
}
