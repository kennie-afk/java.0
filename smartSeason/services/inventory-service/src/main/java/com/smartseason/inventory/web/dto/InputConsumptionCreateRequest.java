package com.smartseason.inventory.web.dto;

import com.smartseason.inventory.domain.InputConsumption;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record InputConsumptionCreateRequest(
        UUID inputIssueId,
        @NotNull UUID farmId,
        UUID plotId,
        UUID seasonId,
        @NotBlank @Size(max = 255) String inputCode,
        @NotNull BigDecimal quantity,
        @NotBlank @Size(max = 255) String unit,
        @NotNull Instant appliedAt,
        UUID appliedBy,
        BigDecimal areaCoveredHa,
        @Size(max = 255) String evidenceUrl,
        BigDecimal varianceKg) {
}
