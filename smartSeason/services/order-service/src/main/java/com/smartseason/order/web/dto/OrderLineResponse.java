package com.smartseason.order.web.dto;

import com.smartseason.order.domain.OrderLine;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record OrderLineResponse(
        UUID id,
        UUID orderId,
        UUID listingId,
        String commodityCode,
        String grade,
        BigDecimal quantity,
        String unit,
        BigDecimal unitPrice,
        BigDecimal lineTotal,
        UUID batchId,
        BigDecimal fulfilledQuantity,
        Instant createdAt,
        Instant updatedAt) {

    public static OrderLineResponse from(OrderLine entity) {
        return new OrderLineResponse(
                entity.getId(),
                entity.getOrderId(),
                entity.getListingId(),
                entity.getCommodityCode(),
                entity.getGrade(),
                entity.getQuantity(),
                entity.getUnit(),
                entity.getUnitPrice(),
                entity.getLineTotal(),
                entity.getBatchId(),
                entity.getFulfilledQuantity(),
                entity.getCreatedAt(),
                entity.getUpdatedAt());
    }
}
