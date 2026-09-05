package com.smartseason.attendance.web.dto;

import com.smartseason.attendance.domain.Geofence;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record GeofenceResponse(
        UUID id,
        UUID farmId,
        UUID plotId,
        String name,
        BigDecimal centerLat,
        BigDecimal centerLng,
        Integer radiusM,
        Boolean active,
        Instant createdAt,
        Instant updatedAt) {

    public static GeofenceResponse from(Geofence entity) {
        return new GeofenceResponse(
                entity.getId(),
                entity.getFarmId(),
                entity.getPlotId(),
                entity.getName(),
                entity.getCenterLat(),
                entity.getCenterLng(),
                entity.getRadiusM(),
                entity.getActive(),
                entity.getCreatedAt(),
                entity.getUpdatedAt());
    }
}
