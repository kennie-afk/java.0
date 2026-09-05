package com.smartseason.inventory.web.dto;

import com.smartseason.inventory.domain.GradingResult;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record GradingResultUpdateRequest(
        UUID batchId,
        UUID gradedBy,
        Instant gradedAt,
        @Size(max = 255) String assignedGrade,
        BigDecimal sizeMm,
        BigDecimal defectPct,
        BigDecimal moisturePct,
        BigDecimal rejectedKg,
        String notes,
        Integer standardVersion) {
}
