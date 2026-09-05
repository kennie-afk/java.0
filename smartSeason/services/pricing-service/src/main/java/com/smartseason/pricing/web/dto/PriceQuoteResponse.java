package com.smartseason.pricing.web.dto;

import com.smartseason.pricing.domain.PriceQuote;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record PriceQuoteResponse(
        UUID id,
        String commodityCode,
        String grade,
        String county,
        BigDecimal quantity,
        BigDecimal suggestedPrice,
        BigDecimal confidence,
        String currency,
        Instant validUntil,
        String rationale,
        UUID requestedBy,
        Instant createdAt,
        Instant updatedAt) {

    public static PriceQuoteResponse from(PriceQuote entity) {
        return new PriceQuoteResponse(
                entity.getId(),
                entity.getCommodityCode(),
                entity.getGrade(),
                entity.getCounty(),
                entity.getQuantity(),
                entity.getSuggestedPrice(),
                entity.getConfidence(),
                entity.getCurrency(),
                entity.getValidUntil(),
                entity.getRationale(),
                entity.getRequestedBy(),
                entity.getCreatedAt(),
                entity.getUpdatedAt());
    }
}
