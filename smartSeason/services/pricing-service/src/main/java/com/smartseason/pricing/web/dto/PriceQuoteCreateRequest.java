package com.smartseason.pricing.web.dto;

import com.smartseason.pricing.domain.PriceQuote;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record PriceQuoteCreateRequest(
        @NotBlank @Size(max = 255) String commodityCode,
        @Size(max = 255) String grade,
        @Size(max = 255) String county,
        BigDecimal quantity,
        @NotNull BigDecimal suggestedPrice,
        BigDecimal confidence,
        @NotBlank @Size(max = 255) String currency,
        Instant validUntil,
        String rationale,
        UUID requestedBy) {
}
