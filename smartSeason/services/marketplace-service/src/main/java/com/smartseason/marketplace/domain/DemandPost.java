package com.smartseason.marketplace.domain;

import com.smartseason.marketplace.platform.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "demand_posts", indexes = {
        @Index(name = "ix_demand_posts_buyer_org_id", columnList = "buyer_org_id"),
        @Index(name = "ix_demand_posts_commodity_code", columnList = "commodity_code"),
        @Index(name = "ix_demand_posts_delivery_county", columnList = "delivery_county")
})
public class DemandPost extends BaseEntity {

    @Column(name = "buyer_org_id", nullable = false)
    private UUID buyerOrgId;

    @Column(name = "commodity_code", nullable = false)
    private String commodityCode;

    @Column(name = "grade")
    private String grade;

    @Column(name = "quantity", nullable = false)
    private BigDecimal quantity;

    @Column(name = "unit", nullable = false)
    private String unit;

    @Column(name = "bid_price")
    private BigDecimal bidPrice;

    @Column(name = "currency", nullable = false)
    private String currency;

    @Column(name = "needed_by")
    private LocalDate neededBy;

    @Column(name = "delivery_county")
    private String deliveryCounty;

    @Column(name = "recurring", nullable = false)
    private Boolean recurring;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private Status status;

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    public UUID getBuyerOrgId() { return buyerOrgId; }
    public void setBuyerOrgId(UUID buyerOrgId) { this.buyerOrgId = buyerOrgId; }

    public String getCommodityCode() { return commodityCode; }
    public void setCommodityCode(String commodityCode) { this.commodityCode = commodityCode; }

    public String getGrade() { return grade; }
    public void setGrade(String grade) { this.grade = grade; }

    public BigDecimal getQuantity() { return quantity; }
    public void setQuantity(BigDecimal quantity) { this.quantity = quantity; }

    public String getUnit() { return unit; }
    public void setUnit(String unit) { this.unit = unit; }

    public BigDecimal getBidPrice() { return bidPrice; }
    public void setBidPrice(BigDecimal bidPrice) { this.bidPrice = bidPrice; }

    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }

    public LocalDate getNeededBy() { return neededBy; }
    public void setNeededBy(LocalDate neededBy) { this.neededBy = neededBy; }

    public String getDeliveryCounty() { return deliveryCounty; }
    public void setDeliveryCounty(String deliveryCounty) { this.deliveryCounty = deliveryCounty; }

    public Boolean getRecurring() { return recurring; }
    public void setRecurring(Boolean recurring) { this.recurring = recurring; }

    public Status getStatus() { return status; }
    public void setStatus(Status status) { this.status = status; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }

    public enum Status { OPEN, PARTIALLY_FILLED, FILLED, EXPIRED, CANCELLED }

}
