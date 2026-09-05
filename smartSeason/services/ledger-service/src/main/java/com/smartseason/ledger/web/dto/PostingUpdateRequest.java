package com.smartseason.ledger.web.dto;

import com.smartseason.ledger.domain.Posting;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record PostingUpdateRequest(
        UUID journalEntryId,
        UUID accountId,
        @Size(max = 255) String accountCode,
        Posting.Direction direction,
        BigDecimal amount,
        @Size(max = 255) String currency,
        Instant postedAt,
        @Size(max = 255) String memo) {
}
