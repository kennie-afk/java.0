package com.smartseason.ledger.web.dto;

import com.smartseason.ledger.domain.AccountBalance;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record AccountBalanceCreateRequest(
        @NotNull UUID accountId,
        @NotBlank @Size(max = 255) String accountCode,
        @NotBlank @Size(max = 255) String currency,
        @NotNull BigDecimal debitTotal,
        @NotNull BigDecimal creditTotal,
        @NotNull BigDecimal balance,
        @NotNull Long postingCount,
        Instant lastPostedAt) {
}
