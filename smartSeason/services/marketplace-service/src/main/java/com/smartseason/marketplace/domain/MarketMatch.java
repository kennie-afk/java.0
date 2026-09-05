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
@Table(name = "market_matches", indexes = {
        @Index(name = "ix_market_matches_listing_id", columnList = "listing_id"),
        @Index(name = "ix_market_matches_demand_post_id", columnList = "demand_post_id")
})
public class MarketMatch extends BaseEntity {

    @Column(name = "listing_id", nullable = false)
    private UUID listingId;

    @Column(name = "demand_post_id", nullable = false)
    private UUID demandPostId;

    @Column(name = "score", nullable = false)
    private BigDecimal score;

    @Column(name = "matched_at", nullable = false)
    private Instant matchedAt;

    @Column(name = "quantity")
    private BigDecimal quantity;

    @Column(name = "order_id")
    private UUID orderId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private Status status;

    public UUID getListingId() { return listingId; }
    public void setListingId(UUID listingId) { this.listingId = listingId; }

    public UUID getDemandPostId() { return demandPostId; }
    public void setDemandPostId(UUID demandPostId) { this.demandPostId = demandPostId; }

    public BigDecimal getScore() { return score; }
    public void setScore(BigDecimal score) { this.score = score; }

    public Instant getMatchedAt() { return matchedAt; }
    public void setMatchedAt(Instant matchedAt) { this.matchedAt = matchedAt; }

    public BigDecimal getQuantity() { return quantity; }
    public void setQuantity(BigDecimal quantity) { this.quantity = quantity; }

    public UUID getOrderId() { return orderId; }
    public void setOrderId(UUID orderId) { this.orderId = orderId; }

    public Status getStatus() { return status; }
    public void setStatus(Status status) { this.status = status; }

    public enum Status { SUGGESTED, ACCEPTED, DECLINED, ORDERED }

}
