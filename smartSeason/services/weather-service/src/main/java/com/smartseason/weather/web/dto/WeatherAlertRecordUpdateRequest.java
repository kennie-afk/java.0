package com.smartseason.weather.web.dto;

import com.smartseason.weather.domain.WeatherAlertRecord;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;

public record WeatherAlertRecordUpdateRequest(
        @Size(max = 255) String geoCell,
        WeatherAlertRecord.AlertType alertType,
        WeatherAlertRecord.Severity severity,
        Instant startsAt,
        Instant endsAt,
        @Size(max = 255) String headline,
        String body,
        @Size(max = 255) String source) {
}
