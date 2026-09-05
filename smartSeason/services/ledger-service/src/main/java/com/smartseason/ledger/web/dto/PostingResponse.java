package com.smartseason.ledger.web.dto;

import com.smartseason.ledger.domain.Posting;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record PostingResponse(
        UUID id,
        UUID journalEntryId,
        UUID accountId,
        String accountCode,
        Posting.Direction direction,
        BigDecimal amount,
        String currency,
        Instant postedAt,
        String memo,
        Instant createdAt,
        Instant updatedAt) {

    public static PostingResponse from(Posting entity) {
        return new PostingResponse(
                entity.getId(),
                entity.getJournalEntryId(),
                entity.getAccountId(),
                entity.getAccountCode(),
                entity.getDirection(),
                entity.getAmount(),
                entity.getCurrency(),
                entity.getPostedAt(),
                entity.getMemo(),
                entity.getCreatedAt(),
                entity.getUpdatedAt());
    }
}
