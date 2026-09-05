package com.smartseason.analytics.web.dto;

import com.smartseason.analytics.domain.Report;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record ReportUpdateRequest(
        @Size(max = 255) String code,
        @Size(max = 255) String name,
        String description,
        @Size(max = 255) String category,
        String querySpec,
        @Size(max = 255) String schedule,
        Report.Format format,
        Boolean enabled,
        UUID ownerUserId) {
}
