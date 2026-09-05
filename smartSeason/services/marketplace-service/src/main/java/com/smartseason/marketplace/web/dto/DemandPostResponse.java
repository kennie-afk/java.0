package com.smartseason.marketplace.web.dto;

import com.smartseason.marketplace.domain.DemandPost;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record DemandPostResponse(
        UUID id,
        UUID buyerOrgId,
        String commodityCode,
        String grade,
        BigDecimal quantity,
        String unit,
        BigDecimal bidPrice,
        String currency,
        LocalDate neededBy,
        String deliveryCounty,
        Boolean recurring,
        DemandPost.Status status,
        String notes,
        Instant createdAt,
        Instant updatedAt) {

    public static DemandPostResponse from(DemandPost entity) {
        return new DemandPostResponse(
                entity.getId(),
                entity.getBuyerOrgId(),
                entity.getCommodityCode(),
                entity.getGrade(),
                entity.getQuantity(),
                entity.getUnit(),
                entity.getBidPrice(),
                entity.getCurrency(),
                entity.getNeededBy(),
                entity.getDeliveryCounty(),
                entity.getRecurring(),
                entity.getStatus(),
                entity.getNotes(),
                entity.getCreatedAt(),
                entity.getUpdatedAt());
    }
}
