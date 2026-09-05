package com.smartseason.weather.web.dto;

import com.smartseason.weather.domain.Forecast;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

public record ForecastCreateRequest(
        @NotBlank @Size(max = 255) String geoCell,
        @NotNull LocalDate forecastFor,
        @NotNull Instant issuedAt,
        BigDecimal tempMinC,
        BigDecimal tempMaxC,
        BigDecimal rainfallMm,
        BigDecimal humidityPct,
        BigDecimal windKph,
        @Size(max = 255) String conditions,
        @Size(max = 255) String provider) {
}
