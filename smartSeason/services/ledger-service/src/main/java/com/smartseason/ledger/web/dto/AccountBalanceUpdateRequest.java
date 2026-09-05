package com.smartseason.ledger.web.dto;

import com.smartseason.ledger.domain.AccountBalance;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record AccountBalanceUpdateRequest(
        UUID accountId,
        @Size(max = 255) String accountCode,
        @Size(max = 255) String currency,
        BigDecimal debitTotal,
        BigDecimal creditTotal,
        BigDecimal balance,
        Long postingCount,
        Instant lastPostedAt) {
}
