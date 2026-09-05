package com.smartseason.marketplace.web.dto;

import com.smartseason.marketplace.domain.MarketMatch;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record MarketMatchCreateRequest(
        @NotNull UUID listingId,
        @NotNull UUID demandPostId,
        @NotNull BigDecimal score,
        @NotNull Instant matchedAt,
        BigDecimal quantity,
        UUID orderId,
        @NotNull MarketMatch.Status status) {
}
