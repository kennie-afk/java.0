package com.smartseason.ledger.web.dto;

import com.smartseason.ledger.domain.AccountBalance;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record AccountBalanceResponse(
        UUID id,
        UUID accountId,
        String accountCode,
        String currency,
        BigDecimal debitTotal,
        BigDecimal creditTotal,
        BigDecimal balance,
        Long postingCount,
        Instant lastPostedAt,
        Instant createdAt,
        Instant updatedAt) {

    public static AccountBalanceResponse from(AccountBalance entity) {
        return new AccountBalanceResponse(
                entity.getId(),
                entity.getAccountId(),
                entity.getAccountCode(),
                entity.getCurrency(),
                entity.getDebitTotal(),
                entity.getCreditTotal(),
                entity.getBalance(),
                entity.getPostingCount(),
                entity.getLastPostedAt(),
                entity.getCreatedAt(),
                entity.getUpdatedAt());
    }
}
