package com.smartseason.payment.web.dto;

import com.smartseason.payment.domain.Wallet;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record WalletResponse(
        UUID id,
        UUID ownerOrgId,
        UUID ownerUserId,
        String currency,
        BigDecimal balance,
        BigDecimal availableBalance,
        Wallet.Status status,
        Instant lastTransactionAt,
        Instant createdAt,
        Instant updatedAt) {

    public static WalletResponse from(Wallet entity) {
        return new WalletResponse(
                entity.getId(),
                entity.getOwnerOrgId(),
                entity.getOwnerUserId(),
                entity.getCurrency(),
                entity.getBalance(),
                entity.getAvailableBalance(),
                entity.getStatus(),
                entity.getLastTransactionAt(),
                entity.getCreatedAt(),
                entity.getUpdatedAt());
    }
}
