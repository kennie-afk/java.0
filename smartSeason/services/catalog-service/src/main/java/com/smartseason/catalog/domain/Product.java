package com.smartseason.catalog.domain;

import com.smartseason.catalog.platform.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

@Entity
@Table(name = "products", indexes = {
        @Index(name = "ix_products_commodity_code", columnList = "commodity_code")
})
public class Product extends BaseEntity {

    @Column(name = "commodity_code", nullable = false)
    private String commodityCode;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "default_grade")
    private String defaultGrade;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private Status status;

    public String getCommodityCode() { return commodityCode; }
    public void setCommodityCode(String commodityCode) { this.commodityCode = commodityCode; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getDefaultGrade() { return defaultGrade; }
    public void setDefaultGrade(String defaultGrade) { this.defaultGrade = defaultGrade; }

    public Status getStatus() { return status; }
    public void setStatus(Status status) { this.status = status; }

    public enum Status { DRAFT, PUBLISHED, ARCHIVED }

}
