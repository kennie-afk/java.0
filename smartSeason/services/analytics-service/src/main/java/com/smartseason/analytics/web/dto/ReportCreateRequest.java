package com.smartseason.analytics.web.dto;

import com.smartseason.analytics.domain.Report;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record ReportCreateRequest(
        @NotBlank @Size(max = 255) String code,
        @NotBlank @Size(max = 255) String name,
        String description,
        @Size(max = 255) String category,
        @NotBlank String querySpec,
        @Size(max = 255) String schedule,
        @NotNull Report.Format format,
        @NotNull Boolean enabled,
        UUID ownerUserId) {
}
