package com.smartseason.inventory.web.dto;

import com.smartseason.inventory.domain.GradingResult;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record GradingResultResponse(
        UUID id,
        UUID batchId,
        UUID gradedBy,
        Instant gradedAt,
        String assignedGrade,
        BigDecimal sizeMm,
        BigDecimal defectPct,
        BigDecimal moisturePct,
        BigDecimal rejectedKg,
        String notes,
        Integer standardVersion,
        Instant createdAt,
        Instant updatedAt) {

    public static GradingResultResponse from(GradingResult entity) {
        return new GradingResultResponse(
                entity.getId(),
                entity.getBatchId(),
                entity.getGradedBy(),
                entity.getGradedAt(),
                entity.getAssignedGrade(),
                entity.getSizeMm(),
                entity.getDefectPct(),
                entity.getMoisturePct(),
                entity.getRejectedKg(),
                entity.getNotes(),
                entity.getStandardVersion(),
                entity.getCreatedAt(),
                entity.getUpdatedAt());
    }
}
