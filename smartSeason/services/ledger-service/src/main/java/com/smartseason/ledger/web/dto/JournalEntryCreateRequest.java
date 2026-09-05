package com.smartseason.ledger.web.dto;

import com.smartseason.ledger.domain.JournalEntry;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record JournalEntryCreateRequest(
        @NotBlank @Size(max = 255) String entryNumber,
        @NotBlank @Size(max = 255) String description,
        @Size(max = 255) String sourceEvent,
        @Size(max = 255) String sourceRef,
        @NotNull Instant postedAt,
        @NotNull LocalDate effectiveDate,
        @NotBlank @Size(max = 255) String currency,
        @NotNull BigDecimal totalDebit,
        @NotNull BigDecimal totalCredit,
        @NotNull Boolean balanced,
        UUID reversalOfId,
        @NotBlank @Size(max = 255) String idempotencyKey) {
}
