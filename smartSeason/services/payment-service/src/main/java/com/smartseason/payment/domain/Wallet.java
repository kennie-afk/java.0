package com.smartseason.payment.domain;

import com.smartseason.payment.platform.BaseEntity;
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
@Table(name = "wallets", indexes = {
        @Index(name = "ix_wallets_owner_org_id", columnList = "owner_org_id"),
        @Index(name = "ix_wallets_owner_user_id", columnList = "owner_user_id")
})
public class Wallet extends BaseEntity {

    @Column(name = "owner_org_id")
    private UUID ownerOrgId;

    @Column(name = "owner_user_id")
    private UUID ownerUserId;

    @Column(name = "currency", nullable = false)
    private String currency;

    @Column(name = "balance", nullable = false)
    private BigDecimal balance;

    @Column(name = "available_balance", nullable = false)
    private BigDecimal availableBalance;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private Status status;

    @Column(name = "last_transaction_at")
    private Instant lastTransactionAt;

    public UUID getOwnerOrgId() { return ownerOrgId; }
    public void setOwnerOrgId(UUID ownerOrgId) { this.ownerOrgId = ownerOrgId; }

    public UUID getOwnerUserId() { return ownerUserId; }
    public void setOwnerUserId(UUID ownerUserId) { this.ownerUserId = ownerUserId; }

    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }

    public BigDecimal getBalance() { return balance; }
    public void setBalance(BigDecimal balance) { this.balance = balance; }

    public BigDecimal getAvailableBalance() { return availableBalance; }
    public void setAvailableBalance(BigDecimal availableBalance) { this.availableBalance = availableBalance; }

    public Status getStatus() { return status; }
    public void setStatus(Status status) { this.status = status; }

    public Instant getLastTransactionAt() { return lastTransactionAt; }
    public void setLastTransactionAt(Instant lastTransactionAt) { this.lastTransactionAt = lastTransactionAt; }

    public enum Status { ACTIVE, FROZEN, CLOSED }

}
