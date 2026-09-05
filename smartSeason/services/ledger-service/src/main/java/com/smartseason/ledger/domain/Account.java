package com.smartseason.ledger.domain;

import com.smartseason.ledger.platform.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "accounts", indexes = {
        @Index(name = "ix_accounts_account_code", columnList = "account_code"),
        @Index(name = "ix_accounts_owner_org_id", columnList = "owner_org_id"),
        @Index(name = "ix_accounts_owner_user_id", columnList = "owner_user_id")
})
public class Account extends BaseEntity {

    @Column(name = "account_code", nullable = false, unique = true)
    private String accountCode;

    @Column(name = "name", nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "account_type", nullable = false)
    private AccountType accountType;

    @Column(name = "owner_org_id")
    private UUID ownerOrgId;

    @Column(name = "owner_user_id")
    private UUID ownerUserId;

    @Column(name = "currency", nullable = false)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(name = "normal_balance", nullable = false)
    private NormalBalance normalBalance;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private Status status;

    @Column(name = "parent_account_id")
    private UUID parentAccountId;

    public String getAccountCode() { return accountCode; }
    public void setAccountCode(String accountCode) { this.accountCode = accountCode; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public AccountType getAccountType() { return accountType; }
    public void setAccountType(AccountType accountType) { this.accountType = accountType; }

    public UUID getOwnerOrgId() { return ownerOrgId; }
    public void setOwnerOrgId(UUID ownerOrgId) { this.ownerOrgId = ownerOrgId; }

    public UUID getOwnerUserId() { return ownerUserId; }
    public void setOwnerUserId(UUID ownerUserId) { this.ownerUserId = ownerUserId; }

    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }

    public NormalBalance getNormalBalance() { return normalBalance; }
    public void setNormalBalance(NormalBalance normalBalance) { this.normalBalance = normalBalance; }

    public Status getStatus() { return status; }
    public void setStatus(Status status) { this.status = status; }

    public UUID getParentAccountId() { return parentAccountId; }
    public void setParentAccountId(UUID parentAccountId) { this.parentAccountId = parentAccountId; }

    public enum AccountType { ASSET, LIABILITY, EQUITY, REVENUE, EXPENSE }

    public enum NormalBalance { DEBIT, CREDIT }

    public enum Status { ACTIVE, FROZEN, CLOSED }

}
