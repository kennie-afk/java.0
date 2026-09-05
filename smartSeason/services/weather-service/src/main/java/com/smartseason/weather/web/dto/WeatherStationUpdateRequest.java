package com.smartseason.weather.web.dto;

import com.smartseason.weather.domain.WeatherStation;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public record WeatherStationUpdateRequest(
        @Size(max = 255) String externalId,
        @Size(max = 255) String name,
        BigDecimal latitude,
        BigDecimal longitude,
        BigDecimal elevationM,
        @Size(max = 255) String provider,
        @Size(max = 255) String county,
        Boolean active) {
}
