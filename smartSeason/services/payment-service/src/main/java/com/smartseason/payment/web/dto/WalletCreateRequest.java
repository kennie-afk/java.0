package com.smartseason.payment.web.dto;

import com.smartseason.payment.domain.Wallet;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record WalletCreateRequest(
        UUID ownerOrgId,
        UUID ownerUserId,
        @NotBlank @Size(max = 255) String currency,
        @NotNull BigDecimal balance,
        @NotNull BigDecimal availableBalance,
        @NotNull Wallet.Status status,
        Instant lastTransactionAt) {
}
