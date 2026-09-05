package com.smartseason.catalog.web.dto;

import com.smartseason.catalog.domain.ProductVariant;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record ProductVariantResponse(
        UUID id,
        UUID productId,
        String sku,
        String variantName,
        BigDecimal packSize,
        String packUnit,
        String grade,
        Boolean active,
        Instant createdAt,
        Instant updatedAt) {

    public static ProductVariantResponse from(ProductVariant entity) {
        return new ProductVariantResponse(
                entity.getId(),
                entity.getProductId(),
                entity.getSku(),
                entity.getVariantName(),
                entity.getPackSize(),
                entity.getPackUnit(),
                entity.getGrade(),
                entity.getActive(),
                entity.getCreatedAt(),
                entity.getUpdatedAt());
    }
}
