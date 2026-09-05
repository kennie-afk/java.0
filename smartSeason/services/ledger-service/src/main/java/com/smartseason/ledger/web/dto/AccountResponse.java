package com.smartseason.ledger.web.dto;

import com.smartseason.ledger.domain.Account;
import java.time.Instant;
import java.util.UUID;

public record AccountResponse(
        UUID id,
        String accountCode,
        String name,
        Account.AccountType accountType,
        UUID ownerOrgId,
        UUID ownerUserId,
        String currency,
        Account.NormalBalance normalBalance,
        Account.Status status,
        UUID parentAccountId,
        Instant createdAt,
        Instant updatedAt) {

    public static AccountResponse from(Account entity) {
        return new AccountResponse(
                entity.getId(),
                entity.getAccountCode(),
                entity.getName(),
                entity.getAccountType(),
                entity.getOwnerOrgId(),
                entity.getOwnerUserId(),
                entity.getCurrency(),
                entity.getNormalBalance(),
                entity.getStatus(),
                entity.getParentAccountId(),
                entity.getCreatedAt(),
                entity.getUpdatedAt());
    }
}
