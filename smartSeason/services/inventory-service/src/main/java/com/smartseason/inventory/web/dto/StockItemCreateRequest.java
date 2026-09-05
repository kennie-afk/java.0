package com.smartseason.inventory.web.dto;

import com.smartseason.inventory.domain.StockItem;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record StockItemCreateRequest(
        @NotNull UUID warehouseId,
        @NotBlank @Size(max = 255) String commodityCode,
        @Size(max = 255) String grade,
        UUID batchId,
        @NotNull BigDecimal quantity,
        @NotBlank @Size(max = 255) String unit,
        @NotNull BigDecimal reservedQuantity,
        LocalDate expiresOn,
        Instant lastCountedAt) {
}
