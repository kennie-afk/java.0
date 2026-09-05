package com.smartseason.pricing.web.dto;

import com.smartseason.pricing.domain.MarketIndex;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record MarketIndexResponse(
        UUID id,
        String commodityCode,
        String region,
        LocalDate periodStart,
        LocalDate periodEnd,
        BigDecimal indexValue,
        BigDecimal changePct,
        String basis,
        Instant computedAt,
        Instant createdAt,
        Instant updatedAt) {

    public static MarketIndexResponse from(MarketIndex entity) {
        return new MarketIndexResponse(
                entity.getId(),
                entity.getCommodityCode(),
                entity.getRegion(),
                entity.getPeriodStart(),
                entity.getPeriodEnd(),
                entity.getIndexValue(),
                entity.getChangePct(),
                entity.getBasis(),
                entity.getComputedAt(),
                entity.getCreatedAt(),
                entity.getUpdatedAt());
    }
}
