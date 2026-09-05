package com.smartseason.inventory.web.dto;

import com.smartseason.inventory.domain.Batch;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record BatchCreateRequest(
        @NotBlank @Size(max = 255) String batchCode,
        @NotBlank @Size(max = 255) String commodityCode,
        UUID farmId,
        UUID plotId,
        UUID seasonId,
        LocalDate harvestedOn,
        Instant receivedAt,
        UUID warehouseId,
        BigDecimal grossWeightKg,
        BigDecimal netWeightKg,
        @Size(max = 255) String grade,
        BigDecimal moisturePct,
        @NotNull Batch.Status status) {
}
