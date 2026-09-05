package com.smartseason.catalog.web.dto;

import com.smartseason.catalog.domain.Product;
import java.time.Instant;
import java.util.UUID;

public record ProductResponse(
        UUID id,
        String commodityCode,
        String name,
        String description,
        String defaultGrade,
        Product.Status status,
        Instant createdAt,
        Instant updatedAt) {

    public static ProductResponse from(Product entity) {
        return new ProductResponse(
                entity.getId(),
                entity.getCommodityCode(),
                entity.getName(),
                entity.getDescription(),
                entity.getDefaultGrade(),
                entity.getStatus(),
                entity.getCreatedAt(),
                entity.getUpdatedAt());
    }
}
