package com.smartseason.catalog.domain;

import com.smartseason.catalog.platform.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

@Entity
@Table(name = "commodities", indexes = {
        @Index(name = "ix_commodities_code", columnList = "code"),
        @Index(name = "ix_commodities_category", columnList = "category")
})
public class Commodity extends BaseEntity {

    @Column(name = "code", nullable = false, unique = true)
    private String code;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "category")
    private String category;

    @Column(name = "default_unit", nullable = false)
    private String defaultUnit;

    @Column(name = "perishable", nullable = false)
    private Boolean perishable;

    @Column(name = "shelf_life_days")
    private Integer shelfLifeDays;

    @Column(name = "image_url")
    private String imageUrl;

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public String getDefaultUnit() { return defaultUnit; }
    public void setDefaultUnit(String defaultUnit) { this.defaultUnit = defaultUnit; }

    public Boolean getPerishable() { return perishable; }
    public void setPerishable(Boolean perishable) { this.perishable = perishable; }

    public Integer getShelfLifeDays() { return shelfLifeDays; }
    public void setShelfLifeDays(Integer shelfLifeDays) { this.shelfLifeDays = shelfLifeDays; }

    public String getImageUrl() { return imageUrl; }
    public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }

}
