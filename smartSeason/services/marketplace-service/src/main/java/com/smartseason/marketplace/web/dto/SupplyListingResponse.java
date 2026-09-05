package com.smartseason.marketplace.web.dto;

import com.smartseason.marketplace.domain.SupplyListing;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record SupplyListingResponse(
        UUID id,
        UUID sellerOrgId,
        UUID farmId,
        String commodityCode,
        String variety,
        String grade,
        BigDecimal quantity,
        String unit,
        BigDecimal askPrice,
        String currency,
        LocalDate availableFrom,
        LocalDate availableTo,
        String county,
        BigDecimal latitude,
        BigDecimal longitude,
        UUID batchId,
        String photoUrls,
        String description,
        SupplyListing.Status status,
        Instant createdAt,
        Instant updatedAt) {

    public static SupplyListingResponse from(SupplyListing entity) {
        return new SupplyListingResponse(
                entity.getId(),
                entity.getSellerOrgId(),
                entity.getFarmId(),
                entity.getCommodityCode(),
                entity.getVariety(),
                entity.getGrade(),
                entity.getQuantity(),
                entity.getUnit(),
                entity.getAskPrice(),
                entity.getCurrency(),
                entity.getAvailableFrom(),
                entity.getAvailableTo(),
                entity.getCounty(),
                entity.getLatitude(),
                entity.getLongitude(),
                entity.getBatchId(),
                entity.getPhotoUrls(),
                entity.getDescription(),
                entity.getStatus(),
                entity.getCreatedAt(),
                entity.getUpdatedAt());
    }
}
