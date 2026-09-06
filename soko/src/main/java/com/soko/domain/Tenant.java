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
@Table(name = "tenants")
public class Tenant {

    @Id @GeneratedValue private UUID id;

    @Column(nullable = false) private String name;
    @Column(nullable = false, unique = true) private String slug;
    @Column(name = "created_at", nullable = false) private Instant createdAt = Instant.now();

    public String getName() { return name; }
    public void setName(String v) { this.name = v; }
    public String getSlug() { return slug; }
    public void setSlug(String v) { this.slug = v; }
    public Instant getCreatedAt() { return createdAt; }

    public UUID getId() { return id; }
    public void setId(UUID v) { this.id = v; }
}
