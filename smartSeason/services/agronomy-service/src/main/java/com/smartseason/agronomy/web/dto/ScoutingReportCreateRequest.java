package com.smartseason.agronomy.web.dto;

import com.smartseason.agronomy.domain.ScoutingReport;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record ScoutingReportCreateRequest(
        @NotNull UUID plotId,
        UUID seasonId,
        @NotNull UUID scoutedBy,
        @NotNull Instant scoutedAt,
        @Size(max = 255) String pestDiseaseCode,
        BigDecimal incidencePct,
        Integer severityScore,
        BigDecimal latitude,
        BigDecimal longitude,
        @Size(max = 255) String photoUrl,
        String notes,
        @NotNull ScoutingReport.Status status) {
}
