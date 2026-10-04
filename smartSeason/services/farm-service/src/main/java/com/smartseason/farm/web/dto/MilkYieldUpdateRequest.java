package com.smartseason.farm.web.dto;

import com.smartseason.farm.domain.MilkYield;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record MilkYieldUpdateRequest(
        UUID cowId,
        UUID farmId,
        LocalDate recordedOn,
        MilkYield.Session session,
        BigDecimal litres,
        UUID recordedBy,
        @Size(max = 255) String notes) {
}
