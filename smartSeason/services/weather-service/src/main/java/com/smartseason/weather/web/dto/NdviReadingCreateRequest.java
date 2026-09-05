package com.smartseason.weather.web.dto;

import com.smartseason.weather.domain.NdviReading;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record NdviReadingCreateRequest(
        UUID plotId,
        @Size(max = 255) String geoCell,
        @NotNull LocalDate capturedOn,
        @NotNull BigDecimal ndvi,
        BigDecimal cloudCoverPct,
        @Size(max = 255) String satellite,
        @Size(max = 255) String tileUrl) {
}
