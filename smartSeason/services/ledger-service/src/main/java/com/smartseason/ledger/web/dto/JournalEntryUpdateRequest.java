package com.smartseason.ledger.web.dto;

import com.smartseason.ledger.domain.JournalEntry;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record JournalEntryUpdateRequest(
        @Size(max = 255) String entryNumber,
        @Size(max = 255) String description,
        @Size(max = 255) String sourceEvent,
        @Size(max = 255) String sourceRef,
        Instant postedAt,
        LocalDate effectiveDate,
        @Size(max = 255) String currency,
        BigDecimal totalDebit,
        BigDecimal totalCredit,
        Boolean balanced,
        UUID reversalOfId,
        @Size(max = 255) String idempotencyKey) {
}
