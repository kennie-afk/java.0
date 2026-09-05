package com.smartseason.weather.web.dto;

import com.smartseason.weather.domain.WeatherAlertRecord;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;

public record WeatherAlertRecordCreateRequest(
        @NotBlank @Size(max = 255) String geoCell,
        @NotNull WeatherAlertRecord.AlertType alertType,
        @NotNull WeatherAlertRecord.Severity severity,
        @NotNull Instant startsAt,
        Instant endsAt,
        @NotBlank @Size(max = 255) String headline,
        String body,
        @Size(max = 255) String source) {
}
