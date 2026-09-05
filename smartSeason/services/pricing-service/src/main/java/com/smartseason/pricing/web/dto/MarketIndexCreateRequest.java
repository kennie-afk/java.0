package com.smartseason.pricing.web.dto;

import com.smartseason.pricing.domain.MarketIndex;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

public record MarketIndexCreateRequest(
        @NotBlank @Size(max = 255) String commodityCode,
        @NotBlank @Size(max = 255) String region,
        @NotNull LocalDate periodStart,
        @NotNull LocalDate periodEnd,
        @NotNull BigDecimal indexValue,
        BigDecimal changePct,
        @Size(max = 255) String basis,
        @NotNull Instant computedAt) {
}
