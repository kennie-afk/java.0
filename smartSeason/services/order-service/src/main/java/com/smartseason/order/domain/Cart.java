package com.smartseason.order.domain;

import com.smartseason.order.platform.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "carts", indexes = {
        @Index(name = "ix_carts_buyer_org_id", columnList = "buyer_org_id")
})
public class Cart extends BaseEntity {

    @Column(name = "buyer_org_id", nullable = false)
    private UUID buyerOrgId;

    @Column(name = "buyer_user_id")
    private UUID buyerUserId;

    @Column(name = "currency", nullable = false)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private Status status;

    @Column(name = "expires_at")
    private Instant expiresAt;

    public UUID getBuyerOrgId() { return buyerOrgId; }
    public void setBuyerOrgId(UUID buyerOrgId) { this.buyerOrgId = buyerOrgId; }

    public UUID getBuyerUserId() { return buyerUserId; }
    public void setBuyerUserId(UUID buyerUserId) { this.buyerUserId = buyerUserId; }

    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }

    public Status getStatus() { return status; }
    public void setStatus(Status status) { this.status = status; }

    public Instant getExpiresAt() { return expiresAt; }
    public void setExpiresAt(Instant expiresAt) { this.expiresAt = expiresAt; }

    public enum Status { OPEN, CHECKED_OUT, ABANDONED }

}
