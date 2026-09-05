package com.smartseason.weather.web.dto;

import com.smartseason.weather.domain.WeatherAlertRecord;
import java.time.Instant;
import java.util.UUID;

public record WeatherAlertRecordResponse(
        UUID id,
        String geoCell,
        WeatherAlertRecord.AlertType alertType,
        WeatherAlertRecord.Severity severity,
        Instant startsAt,
        Instant endsAt,
        String headline,
        String body,
        String source,
        Instant createdAt,
        Instant updatedAt) {

    public static WeatherAlertRecordResponse from(WeatherAlertRecord entity) {
        return new WeatherAlertRecordResponse(
                entity.getId(),
                entity.getGeoCell(),
                entity.getAlertType(),
                entity.getSeverity(),
                entity.getStartsAt(),
                entity.getEndsAt(),
                entity.getHeadline(),
                entity.getBody(),
                entity.getSource(),
                entity.getCreatedAt(),
                entity.getUpdatedAt());
    }
}
