package com.smartseason.pricing.web.dto;

import com.smartseason.pricing.domain.PriceSeries;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;

public record PriceSeriesUpdateRequest(
        @Size(max = 255) String commodityCode,
        @Size(max = 255) String county,
        @Size(max = 255) String marketName,
        @Size(max = 255) String grade,
        LocalDate observedOn,
        @Size(max = 255) String unit,
        BigDecimal minPrice,
        BigDecimal maxPrice,
        BigDecimal avgPrice,
        @Size(max = 255) String currency,
        @Size(max = 255) String source,
        BigDecimal volumeKg) {
}
