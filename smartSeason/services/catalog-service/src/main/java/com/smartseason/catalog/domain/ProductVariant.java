package com.smartseason.catalog.domain;

import com.smartseason.catalog.platform.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "product_variants", indexes = {
        @Index(name = "ix_product_variants_product_id", columnList = "product_id"),
        @Index(name = "ix_product_variants_sku", columnList = "sku")
})
public class ProductVariant extends BaseEntity {

    @Column(name = "product_id", nullable = false)
    private UUID productId;

    @Column(name = "sku", nullable = false, unique = true)
    private String sku;

    @Column(name = "variant_name", nullable = false)
    private String variantName;

    @Column(name = "pack_size")
    private BigDecimal packSize;

    @Column(name = "pack_unit")
    private String packUnit;

    @Column(name = "grade")
    private String grade;

    @Column(name = "active", nullable = false)
    private Boolean active;

    public UUID getProductId() { return productId; }
    public void setProductId(UUID productId) { this.productId = productId; }

    public String getSku() { return sku; }
    public void setSku(String sku) { this.sku = sku; }

    public String getVariantName() { return variantName; }
    public void setVariantName(String variantName) { this.variantName = variantName; }

    public BigDecimal getPackSize() { return packSize; }
    public void setPackSize(BigDecimal packSize) { this.packSize = packSize; }

    public String getPackUnit() { return packUnit; }
    public void setPackUnit(String packUnit) { this.packUnit = packUnit; }

    public String getGrade() { return grade; }
    public void setGrade(String grade) { this.grade = grade; }

    public Boolean getActive() { return active; }
    public void setActive(Boolean active) { this.active = active; }

}
