package com.smartseason.marketplace.web.dto;

import com.smartseason.marketplace.domain.Offer;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record OfferUpdateRequest(
        UUID listingId,
        UUID demandPostId,
        UUID fromOrgId,
        UUID toOrgId,
        BigDecimal quantity,
        BigDecimal unitPrice,
        @Size(max = 255) String currency,
        Instant expiresAt,
        Offer.Status status,
        UUID counterOfferId,
        String message,
        Instant respondedAt) {
}
