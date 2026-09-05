package com.smartseason.inventory.web.dto;

import com.smartseason.inventory.domain.GradingResult;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record GradingResultCreateRequest(
        @NotNull UUID batchId,
        UUID gradedBy,
        @NotNull Instant gradedAt,
        @NotBlank @Size(max = 255) String assignedGrade,
        BigDecimal sizeMm,
        BigDecimal defectPct,
        BigDecimal moisturePct,
        BigDecimal rejectedKg,
        String notes,
        Integer standardVersion) {
}
