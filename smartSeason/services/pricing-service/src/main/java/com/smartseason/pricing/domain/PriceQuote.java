package com.smartseason.pricing.domain;

import com.smartseason.pricing.platform.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "price_quotes", indexes = {
        @Index(name = "ix_price_quotes_commodity_code", columnList = "commodity_code")
})
public class PriceQuote extends BaseEntity {

    @Column(name = "commodity_code", nullable = false)
    private String commodityCode;

    @Column(name = "grade")
    private String grade;

    @Column(name = "county")
    private String county;

    @Column(name = "quantity")
    private BigDecimal quantity;

    @Column(name = "suggested_price", nullable = false)
    private BigDecimal suggestedPrice;

    @Column(name = "confidence")
    private BigDecimal confidence;

    @Column(name = "currency", nullable = false)
    private String currency;

    @Column(name = "valid_until")
    private Instant validUntil;

    @Column(name = "rationale", columnDefinition = "TEXT")
    private String rationale;

    @Column(name = "requested_by")
    private UUID requestedBy;

    public String getCommodityCode() { return commodityCode; }
    public void setCommodityCode(String commodityCode) { this.commodityCode = commodityCode; }

    public String getGrade() { return grade; }
    public void setGrade(String grade) { this.grade = grade; }

    public String getCounty() { return county; }
    public void setCounty(String county) { this.county = county; }

    public BigDecimal getQuantity() { return quantity; }
    public void setQuantity(BigDecimal quantity) { this.quantity = quantity; }

    public BigDecimal getSuggestedPrice() { return suggestedPrice; }
    public void setSuggestedPrice(BigDecimal suggestedPrice) { this.suggestedPrice = suggestedPrice; }

    public BigDecimal getConfidence() { return confidence; }
    public void setConfidence(BigDecimal confidence) { this.confidence = confidence; }

    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }

    public Instant getValidUntil() { return validUntil; }
    public void setValidUntil(Instant validUntil) { this.validUntil = validUntil; }

    public String getRationale() { return rationale; }
    public void setRationale(String rationale) { this.rationale = rationale; }

    public UUID getRequestedBy() { return requestedBy; }
    public void setRequestedBy(UUID requestedBy) { this.requestedBy = requestedBy; }

}
