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
@Table(name = "suppliers")
public class Supplier {

    @Id @GeneratedValue private UUID id;

    @Column(name = "tenant_id", nullable = false) private UUID tenantId;
    @Column(nullable = false) private String name;
    @Column(nullable = false) private String county;
    @Column(name = "lead_time_hours", nullable = false) private int leadTimeHours;
    @Column(name = "cold_chain", nullable = false) private boolean coldChain;
    @Column(nullable = false) private BigDecimal reliability = new BigDecimal("0.900");
    @Column(nullable = false) private String status = "ACTIVE";
    @Column(name = "created_at", nullable = false) private Instant createdAt = Instant.now();

    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID v) { this.tenantId = v; }
    public String getName() { return name; }
    public void setName(String v) { this.name = v; }
    public String getCounty() { return county; }
    public void setCounty(String v) { this.county = v; }
    public int getLeadTimeHours() { return leadTimeHours; }
    public void setLeadTimeHours(int v) { this.leadTimeHours = v; }
    public boolean isColdChain() { return coldChain; }
    public void setColdChain(boolean v) { this.coldChain = v; }
    public BigDecimal getReliability() { return reliability; }
    public void setReliability(BigDecimal v) { this.reliability = v; }
    public String getStatus() { return status; }
    public void setStatus(String v) { this.status = v; }

    public UUID getId() { return id; }
    public void setId(UUID v) { this.id = v; }
}
