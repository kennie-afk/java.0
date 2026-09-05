package com.smartseason.ledger.posting;

import com.smartseason.ledger.domain.Posting;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record PostingRequest(
        @NotBlank @Size(max = 255) String description,
        @NotBlank @Size(max = 3) String currency,
        @NotNull LocalDate effectiveDate,
        @Size(max = 255) String sourceEvent,
        @Size(max = 255) String sourceRef,
        @NotBlank @Size(max = 255) String idempotencyKey,
        @NotEmpty @Valid List<Line> lines) {

    public record Line(
            @NotBlank @Size(max = 255) String accountCode,
            @NotNull Posting.Direction direction,
            @NotNull @DecimalMin(value = "0.0001") BigDecimal amount,
            @Size(max = 255) String memo) {
    }
}
