package com.smartseason.analytics.web.dto;

import com.smartseason.analytics.domain.Report;
import java.time.Instant;
import java.util.UUID;

public record ReportResponse(
        UUID id,
        String code,
        String name,
        String description,
        String category,
        String querySpec,
        String schedule,
        Report.Format format,
        Boolean enabled,
        UUID ownerUserId,
        Instant createdAt,
        Instant updatedAt) {

    public static ReportResponse from(Report entity) {
        return new ReportResponse(
                entity.getId(),
                entity.getCode(),
                entity.getName(),
                entity.getDescription(),
                entity.getCategory(),
                entity.getQuerySpec(),
                entity.getSchedule(),
                entity.getFormat(),
                entity.getEnabled(),
                entity.getOwnerUserId(),
                entity.getCreatedAt(),
                entity.getUpdatedAt());
    }
}
