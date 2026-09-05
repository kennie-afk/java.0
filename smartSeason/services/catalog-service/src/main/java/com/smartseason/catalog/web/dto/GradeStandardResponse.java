package com.smartseason.catalog.web.dto;

import com.smartseason.catalog.domain.GradeStandard;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record GradeStandardResponse(
        UUID id,
        String commodityCode,
        String grade,
        String criteria,
        BigDecimal minSizeMm,
        BigDecimal maxDefectPct,
        BigDecimal moisturePctMax,
        Integer revision,
        Instant createdAt,
        Instant updatedAt) {

    public static GradeStandardResponse from(GradeStandard entity) {
        return new GradeStandardResponse(
                entity.getId(),
                entity.getCommodityCode(),
                entity.getGrade(),
                entity.getCriteria(),
                entity.getMinSizeMm(),
                entity.getMaxDefectPct(),
                entity.getMoisturePctMax(),
                entity.getRevision(),
                entity.getCreatedAt(),
                entity.getUpdatedAt());
    }
}
