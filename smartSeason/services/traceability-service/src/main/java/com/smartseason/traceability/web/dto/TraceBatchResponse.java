package com.smartseason.traceability.web.dto;

import com.smartseason.traceability.domain.TraceBatch;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record TraceBatchResponse(
        UUID id,
        String batchCode,
        String commodityCode,
        UUID farmId,
        UUID plotId,
        UUID seasonId,
        LocalDate harvestedOn,
        String originCounty,
        UUID currentHolderOrgId,
        BigDecimal quantityKg,
        TraceBatch.Status status,
        Instant createdAt,
        Instant updatedAt) {

    public static TraceBatchResponse from(TraceBatch entity) {
        return new TraceBatchResponse(
                entity.getId(),
                entity.getBatchCode(),
                entity.getCommodityCode(),
                entity.getFarmId(),
                entity.getPlotId(),
                entity.getSeasonId(),
                entity.getHarvestedOn(),
                entity.getOriginCounty(),
                entity.getCurrentHolderOrgId(),
                entity.getQuantityKg(),
                entity.getStatus(),
                entity.getCreatedAt(),
                entity.getUpdatedAt());
    }
}
