package com.smartseason.catalog.web.dto;

import com.smartseason.catalog.domain.Commodity;
import java.time.Instant;
import java.util.UUID;

public record CommodityResponse(
        UUID id,
        String code,
        String name,
        String category,
        String defaultUnit,
        Boolean perishable,
        Integer shelfLifeDays,
        String imageUrl,
        Instant createdAt,
        Instant updatedAt) {

    public static CommodityResponse from(Commodity entity) {
        return new CommodityResponse(
                entity.getId(),
                entity.getCode(),
                entity.getName(),
                entity.getCategory(),
                entity.getDefaultUnit(),
                entity.getPerishable(),
                entity.getShelfLifeDays(),
                entity.getImageUrl(),
                entity.getCreatedAt(),
                entity.getUpdatedAt());
    }
}
