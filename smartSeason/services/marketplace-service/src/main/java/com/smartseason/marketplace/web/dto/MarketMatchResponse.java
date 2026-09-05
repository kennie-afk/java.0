package com.smartseason.marketplace.web.dto;

import com.smartseason.marketplace.domain.MarketMatch;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record MarketMatchResponse(
        UUID id,
        UUID listingId,
        UUID demandPostId,
        BigDecimal score,
        Instant matchedAt,
        BigDecimal quantity,
        UUID orderId,
        MarketMatch.Status status,
        Instant createdAt,
        Instant updatedAt) {

    public static MarketMatchResponse from(MarketMatch entity) {
        return new MarketMatchResponse(
                entity.getId(),
                entity.getListingId(),
                entity.getDemandPostId(),
                entity.getScore(),
                entity.getMatchedAt(),
                entity.getQuantity(),
                entity.getOrderId(),
                entity.getStatus(),
                entity.getCreatedAt(),
                entity.getUpdatedAt());
    }
}
