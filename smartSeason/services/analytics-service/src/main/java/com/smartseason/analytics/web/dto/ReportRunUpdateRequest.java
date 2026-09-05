package com.smartseason.analytics.web.dto;

import com.smartseason.analytics.domain.ReportRun;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;

public record ReportRunUpdateRequest(
        UUID reportId,
        @Size(max = 255) String reportCode,
        UUID triggeredBy,
        Instant startedAt,
        Instant completedAt,
        Integer rowCount,
        @Size(max = 255) String outputUrl,
        String parameters,
        ReportRun.Status status,
        String error) {
}
