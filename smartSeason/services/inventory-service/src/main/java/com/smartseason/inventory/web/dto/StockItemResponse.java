package com.smartseason.inventory.web.dto;

import com.smartseason.inventory.domain.StockItem;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record StockItemResponse(
        UUID id,
        UUID warehouseId,
        String commodityCode,
        String grade,
        UUID batchId,
        BigDecimal quantity,
        String unit,
        BigDecimal reservedQuantity,
        LocalDate expiresOn,
        Instant lastCountedAt,
        Instant createdAt,
        Instant updatedAt) {

    public static StockItemResponse from(StockItem entity) {
        return new StockItemResponse(
                entity.getId(),
                entity.getWarehouseId(),
                entity.getCommodityCode(),
                entity.getGrade(),
                entity.getBatchId(),
                entity.getQuantity(),
                entity.getUnit(),
                entity.getReservedQuantity(),
                entity.getExpiresOn(),
                entity.getLastCountedAt(),
                entity.getCreatedAt(),
                entity.getUpdatedAt());
    }
}
