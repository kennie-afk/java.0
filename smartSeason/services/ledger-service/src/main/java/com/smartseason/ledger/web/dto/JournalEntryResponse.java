package com.smartseason.ledger.web.dto;

import com.smartseason.ledger.domain.JournalEntry;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record JournalEntryResponse(
        UUID id,
        String entryNumber,
        String description,
        String sourceEvent,
        String sourceRef,
        Instant postedAt,
        LocalDate effectiveDate,
        String currency,
        BigDecimal totalDebit,
        BigDecimal totalCredit,
        Boolean balanced,
        UUID reversalOfId,
        String idempotencyKey,
        Instant createdAt,
        Instant updatedAt) {

    public static JournalEntryResponse from(JournalEntry entity) {
        return new JournalEntryResponse(
                entity.getId(),
                entity.getEntryNumber(),
                entity.getDescription(),
                entity.getSourceEvent(),
                entity.getSourceRef(),
                entity.getPostedAt(),
                entity.getEffectiveDate(),
                entity.getCurrency(),
                entity.getTotalDebit(),
                entity.getTotalCredit(),
                entity.getBalanced(),
                entity.getReversalOfId(),
                entity.getIdempotencyKey(),
                entity.getCreatedAt(),
                entity.getUpdatedAt());
    }
}
