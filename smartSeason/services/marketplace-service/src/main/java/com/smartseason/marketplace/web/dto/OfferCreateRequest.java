package com.smartseason.marketplace.web.dto;

import com.smartseason.marketplace.domain.Offer;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record OfferCreateRequest(
        UUID listingId,
        UUID demandPostId,
        @NotNull UUID fromOrgId,
        @NotNull UUID toOrgId,
        @NotNull BigDecimal quantity,
        @NotNull BigDecimal unitPrice,
        @NotBlank @Size(max = 255) String currency,
        Instant expiresAt,
        @NotNull Offer.Status status,
        UUID counterOfferId,
        String message,
        Instant respondedAt) {
}
