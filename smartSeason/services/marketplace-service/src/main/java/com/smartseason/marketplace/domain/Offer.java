package com.smartseason.marketplace.domain;

import com.smartseason.marketplace.platform.BaseEntity;
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
@Table(name = "offers", indexes = {
        @Index(name = "ix_offers_listing_id", columnList = "listing_id"),
        @Index(name = "ix_offers_demand_post_id", columnList = "demand_post_id"),
        @Index(name = "ix_offers_from_org_id", columnList = "from_org_id"),
        @Index(name = "ix_offers_to_org_id", columnList = "to_org_id")
})
public class Offer extends BaseEntity {

    @Column(name = "listing_id")
    private UUID listingId;

    @Column(name = "demand_post_id")
    private UUID demandPostId;

    @Column(name = "from_org_id", nullable = false)
    private UUID fromOrgId;

    @Column(name = "to_org_id", nullable = false)
    private UUID toOrgId;

    @Column(name = "quantity", nullable = false)
    private BigDecimal quantity;

    @Column(name = "unit_price", nullable = false)
    private BigDecimal unitPrice;

    @Column(name = "currency", nullable = false)
    private String currency;

    @Column(name = "expires_at")
    private Instant expiresAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private Status status;

    @Column(name = "counter_offer_id")
    private UUID counterOfferId;

    @Column(name = "message", columnDefinition = "TEXT")
    private String message;

    @Column(name = "responded_at")
    private Instant respondedAt;

    public UUID getListingId() { return listingId; }
    public void setListingId(UUID listingId) { this.listingId = listingId; }

    public UUID getDemandPostId() { return demandPostId; }
    public void setDemandPostId(UUID demandPostId) { this.demandPostId = demandPostId; }

    public UUID getFromOrgId() { return fromOrgId; }
    public void setFromOrgId(UUID fromOrgId) { this.fromOrgId = fromOrgId; }

    public UUID getToOrgId() { return toOrgId; }
    public void setToOrgId(UUID toOrgId) { this.toOrgId = toOrgId; }

    public BigDecimal getQuantity() { return quantity; }
    public void setQuantity(BigDecimal quantity) { this.quantity = quantity; }

    public BigDecimal getUnitPrice() { return unitPrice; }
    public void setUnitPrice(BigDecimal unitPrice) { this.unitPrice = unitPrice; }

    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }

    public Instant getExpiresAt() { return expiresAt; }
    public void setExpiresAt(Instant expiresAt) { this.expiresAt = expiresAt; }

    public Status getStatus() { return status; }
    public void setStatus(Status status) { this.status = status; }

    public UUID getCounterOfferId() { return counterOfferId; }
    public void setCounterOfferId(UUID counterOfferId) { this.counterOfferId = counterOfferId; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public Instant getRespondedAt() { return respondedAt; }
    public void setRespondedAt(Instant respondedAt) { this.respondedAt = respondedAt; }

    public enum Status { PENDING, ACCEPTED, REJECTED, COUNTERED, EXPIRED, WITHDRAWN }

}
