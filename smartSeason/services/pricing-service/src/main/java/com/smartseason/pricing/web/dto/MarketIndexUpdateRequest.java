package com.smartseason.pricing.web.dto;

import com.smartseason.pricing.domain.MarketIndex;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

public record MarketIndexUpdateRequest(
        @Size(max = 255) String commodityCode,
        @Size(max = 255) String region,
        LocalDate periodStart,
        LocalDate periodEnd,
        BigDecimal indexValue,
        BigDecimal changePct,
        @Size(max = 255) String basis,
        Instant computedAt) {
}
