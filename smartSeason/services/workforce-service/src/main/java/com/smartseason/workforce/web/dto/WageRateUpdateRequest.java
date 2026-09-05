package com.smartseason.workforce.web.dto;

import com.smartseason.workforce.domain.WageRate;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record WageRateUpdateRequest(
        UUID farmId,
        @Size(max = 255) String taskCode,
        WageRate.RateType rateType,
        BigDecimal amount,
        @Size(max = 255) String currency,
        @Size(max = 255) String unit,
        LocalDate effectiveFrom,
        LocalDate effectiveTo) {
}
