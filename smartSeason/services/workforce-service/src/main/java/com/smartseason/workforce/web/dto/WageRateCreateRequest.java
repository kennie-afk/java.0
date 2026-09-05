package com.smartseason.workforce.web.dto;

import com.smartseason.workforce.domain.WageRate;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record WageRateCreateRequest(
        UUID farmId,
        @NotBlank @Size(max = 255) String taskCode,
        @NotNull WageRate.RateType rateType,
        @NotNull BigDecimal amount,
        @NotBlank @Size(max = 255) String currency,
        @Size(max = 255) String unit,
        @NotNull LocalDate effectiveFrom,
        LocalDate effectiveTo) {
}
