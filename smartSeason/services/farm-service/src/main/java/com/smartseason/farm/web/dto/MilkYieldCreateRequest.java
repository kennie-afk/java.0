package com.smartseason.farm.web.dto;

import com.smartseason.farm.domain.MilkYield;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record MilkYieldCreateRequest(
        @NotNull UUID cowId,
        @NotNull UUID farmId,
        @NotNull LocalDate recordedOn,
        @NotNull MilkYield.Session session,
        @NotNull BigDecimal litres,
        UUID recordedBy,
        @Size(max = 255) String notes) {
}
