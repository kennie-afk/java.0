package com.smartseason.pricing.web.dto;

import com.smartseason.pricing.domain.PriceSeries;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;

public record PriceSeriesCreateRequest(
        @NotBlank @Size(max = 255) String commodityCode,
        @Size(max = 255) String county,
        @Size(max = 255) String marketName,
        @Size(max = 255) String grade,
        @NotNull LocalDate observedOn,
        @NotBlank @Size(max = 255) String unit,
        BigDecimal minPrice,
        BigDecimal maxPrice,
        @NotNull BigDecimal avgPrice,
        @NotBlank @Size(max = 255) String currency,
        @Size(max = 255) String source,
        BigDecimal volumeKg) {
}
