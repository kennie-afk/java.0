package com.smartseason.ledger.domain;

import com.smartseason.ledger.platform.BaseEntity;
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
@Table(name = "account_balances", indexes = {
        @Index(name = "ix_account_balances_account_id", columnList = "account_id"),
        @Index(name = "ix_account_balances_account_code", columnList = "account_code")
})
public class AccountBalance extends BaseEntity {

    @Column(name = "account_id", nullable = false, unique = true)
    private UUID accountId;

    @Column(name = "account_code", nullable = false)
    private String accountCode;

    @Column(name = "currency", nullable = false)
    private String currency;

    @Column(name = "debit_total", nullable = false)
    private BigDecimal debitTotal;

    @Column(name = "credit_total", nullable = false)
    private BigDecimal creditTotal;

    @Column(name = "balance", nullable = false)
    private BigDecimal balance;

    @Column(name = "posting_count", nullable = false)
    private Long postingCount;

    @Column(name = "last_posted_at")
    private Instant lastPostedAt;

    public UUID getAccountId() { return accountId; }
    public void setAccountId(UUID accountId) { this.accountId = accountId; }

    public String getAccountCode() { return accountCode; }
    public void setAccountCode(String accountCode) { this.accountCode = accountCode; }

    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }

    public BigDecimal getDebitTotal() { return debitTotal; }
    public void setDebitTotal(BigDecimal debitTotal) { this.debitTotal = debitTotal; }

    public BigDecimal getCreditTotal() { return creditTotal; }
    public void setCreditTotal(BigDecimal creditTotal) { this.creditTotal = creditTotal; }

    public BigDecimal getBalance() { return balance; }
    public void setBalance(BigDecimal balance) { this.balance = balance; }

    public Long getPostingCount() { return postingCount; }
    public void setPostingCount(Long postingCount) { this.postingCount = postingCount; }

    public Instant getLastPostedAt() { return lastPostedAt; }
    public void setLastPostedAt(Instant lastPostedAt) { this.lastPostedAt = lastPostedAt; }

}
