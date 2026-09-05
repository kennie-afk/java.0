package com.smartseason.analytics.web.dto;

import com.smartseason.analytics.domain.ReportRun;
import java.time.Instant;
import java.util.UUID;

public record ReportRunResponse(
        UUID id,
        UUID reportId,
        String reportCode,
        UUID triggeredBy,
        Instant startedAt,
        Instant completedAt,
        Integer rowCount,
        String outputUrl,
        String parameters,
        ReportRun.Status status,
        String error,
        Instant createdAt,
        Instant updatedAt) {

    public static ReportRunResponse from(ReportRun entity) {
        return new ReportRunResponse(
                entity.getId(),
                entity.getReportId(),
                entity.getReportCode(),
                entity.getTriggeredBy(),
                entity.getStartedAt(),
                entity.getCompletedAt(),
                entity.getRowCount(),
                entity.getOutputUrl(),
                entity.getParameters(),
                entity.getStatus(),
                entity.getError(),
                entity.getCreatedAt(),
                entity.getUpdatedAt());
    }
}
