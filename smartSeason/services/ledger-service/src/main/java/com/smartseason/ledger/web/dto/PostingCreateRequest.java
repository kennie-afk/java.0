package com.smartseason.ledger.web.dto;

import com.smartseason.ledger.domain.Posting;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record PostingCreateRequest(
        @NotNull UUID journalEntryId,
        @NotNull UUID accountId,
        @NotBlank @Size(max = 255) String accountCode,
        @NotNull Posting.Direction direction,
        @NotNull BigDecimal amount,
        @NotBlank @Size(max = 255) String currency,
        @NotNull Instant postedAt,
        @Size(max = 255) String memo) {
}
