package com.smartseason.pricing.web.dto;

import com.smartseason.pricing.domain.PriceSeries;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record PriceSeriesResponse(
        UUID id,
        String commodityCode,
        String county,
        String marketName,
        String grade,
        LocalDate observedOn,
        String unit,
        BigDecimal minPrice,
        BigDecimal maxPrice,
        BigDecimal avgPrice,
        String currency,
        String source,
        BigDecimal volumeKg,
        Instant createdAt,
        Instant updatedAt) {

    public static PriceSeriesResponse from(PriceSeries entity) {
        return new PriceSeriesResponse(
                entity.getId(),
                entity.getCommodityCode(),
                entity.getCounty(),
                entity.getMarketName(),
                entity.getGrade(),
                entity.getObservedOn(),
                entity.getUnit(),
                entity.getMinPrice(),
                entity.getMaxPrice(),
                entity.getAvgPrice(),
                entity.getCurrency(),
                entity.getSource(),
                entity.getVolumeKg(),
                entity.getCreatedAt(),
                entity.getUpdatedAt());
    }
}
