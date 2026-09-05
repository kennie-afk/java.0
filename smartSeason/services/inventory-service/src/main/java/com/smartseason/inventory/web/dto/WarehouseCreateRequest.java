package com.smartseason.inventory.web.dto;

import com.smartseason.inventory.domain.Warehouse;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.UUID;

public record WarehouseCreateRequest(
        @NotBlank @Size(max = 255) String name,
        @Size(max = 255) String county,
        BigDecimal latitude,
        BigDecimal longitude,
        BigDecimal capacityKg,
        @NotNull Boolean coldChain,
        UUID managerUserId,
        @NotNull Warehouse.Status status) {
}
