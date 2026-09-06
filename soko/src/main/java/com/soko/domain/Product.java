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
@Table(name = "products")
public class Product {

    @Id @GeneratedValue private UUID id;

    @Column(name = "tenant_id", nullable = false) private UUID tenantId;
    @Column(nullable = false) private String sku;
    @Column(nullable = false) private String name;
    @Column(nullable = false) private String category;
    @Column(nullable = false) private String unit;
    @Column(nullable = false) private boolean perishable;
    @Column(name = "requires_cold_chain", nullable = false) private boolean requiresColdChain;
    @Column(name = "shelf_life_hours", nullable = false) private int shelfLifeHours;
    @Column(name = "list_price_cents", nullable = false) private long listPriceCents;
    @Column(name = "created_at", nullable = false) private Instant createdAt = Instant.now();

    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID v) { this.tenantId = v; }
    public String getSku() { return sku; }
    public void setSku(String v) { this.sku = v; }
    public String getName() { return name; }
    public void setName(String v) { this.name = v; }
    public String getCategory() { return category; }
    public void setCategory(String v) { this.category = v; }
    public String getUnit() { return unit; }
    public void setUnit(String v) { this.unit = v; }
    public boolean isPerishable() { return perishable; }
    public void setPerishable(boolean v) { this.perishable = v; }
    public boolean isRequiresColdChain() { return requiresColdChain; }
    public void setRequiresColdChain(boolean v) { this.requiresColdChain = v; }
    public int getShelfLifeHours() { return shelfLifeHours; }
    public void setShelfLifeHours(int v) { this.shelfLifeHours = v; }
    public long getListPriceCents() { return listPriceCents; }
    public void setListPriceCents(long v) { this.listPriceCents = v; }

    public UUID getId() { return id; }
    public void setId(UUID v) { this.id = v; }
}
