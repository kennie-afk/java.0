package com.smartseason.inventory.web.dto;

import com.smartseason.inventory.domain.Batch;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record BatchResponse(
        UUID id,
        String batchCode,
        String commodityCode,
        UUID farmId,
        UUID plotId,
        UUID seasonId,
        LocalDate harvestedOn,
        Instant receivedAt,
        UUID warehouseId,
        BigDecimal grossWeightKg,
        BigDecimal netWeightKg,
        String grade,
        BigDecimal moisturePct,
        Batch.Status status,
        Instant createdAt,
        Instant updatedAt) {

    public static BatchResponse from(Batch entity) {
        return new BatchResponse(
                entity.getId(),
                entity.getBatchCode(),
                entity.getCommodityCode(),
                entity.getFarmId(),
                entity.getPlotId(),
                entity.getSeasonId(),
                entity.getHarvestedOn(),
                entity.getReceivedAt(),
                entity.getWarehouseId(),
                entity.getGrossWeightKg(),
                entity.getNetWeightKg(),
                entity.getGrade(),
                entity.getMoisturePct(),
                entity.getStatus(),
                entity.getCreatedAt(),
                entity.getUpdatedAt());
    }
}
