package com.smartseason.marketplace.web.dto;

import com.smartseason.marketplace.domain.Offer;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record OfferResponse(
        UUID id,
        UUID listingId,
        UUID demandPostId,
        UUID fromOrgId,
        UUID toOrgId,
        BigDecimal quantity,
        BigDecimal unitPrice,
        String currency,
        Instant expiresAt,
        Offer.Status status,
        UUID counterOfferId,
        String message,
        Instant respondedAt,
        Instant createdAt,
        Instant updatedAt) {

    public static OfferResponse from(Offer entity) {
        return new OfferResponse(
                entity.getId(),
                entity.getListingId(),
                entity.getDemandPostId(),
                entity.getFromOrgId(),
                entity.getToOrgId(),
                entity.getQuantity(),
                entity.getUnitPrice(),
                entity.getCurrency(),
                entity.getExpiresAt(),
                entity.getStatus(),
                entity.getCounterOfferId(),
                entity.getMessage(),
                entity.getRespondedAt(),
                entity.getCreatedAt(),
                entity.getUpdatedAt());
    }
}
