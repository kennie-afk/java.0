package com.smartseason.weather.web.dto;

import com.smartseason.weather.domain.WeatherStation;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public record WeatherStationCreateRequest(
        @NotBlank @Size(max = 255) String externalId,
        @NotBlank @Size(max = 255) String name,
        @NotNull BigDecimal latitude,
        @NotNull BigDecimal longitude,
        BigDecimal elevationM,
        @NotBlank @Size(max = 255) String provider,
        @Size(max = 255) String county,
        @NotNull Boolean active) {
}
