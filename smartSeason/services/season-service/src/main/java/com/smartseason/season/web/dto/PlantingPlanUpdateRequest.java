package com.smartseason.season.web.dto;

import com.smartseason.season.domain.PlantingPlan;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record PlantingPlanUpdateRequest(
        UUID seasonId,
        BigDecimal seedRateKgHa,
        @Size(max = 255) String spacingCm,
        Integer targetPopulation,
        String fertiliserPlan,
        String irrigationPlan,
        UUID approvedBy,
        Instant approvedAt) {
}
