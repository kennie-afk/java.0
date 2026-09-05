package com.smartseason.logistics.web.dto;

import com.smartseason.logistics.domain.RouteStop;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record RouteStopResponse(
        UUID id,
        UUID transportJobId,
        Integer sequence,
        RouteStop.StopType stopType,
        BigDecimal latitude,
        BigDecimal longitude,
        Instant plannedAt,
        Instant arrivedAt,
        Instant departedAt,
        String notes,
        Boolean offRoute,
        Instant createdAt,
        Instant updatedAt) {

    public static RouteStopResponse from(RouteStop entity) {
        return new RouteStopResponse(
                entity.getId(),
                entity.getTransportJobId(),
                entity.getSequence(),
                entity.getStopType(),
                entity.getLatitude(),
                entity.getLongitude(),
                entity.getPlannedAt(),
                entity.getArrivedAt(),
                entity.getDepartedAt(),
                entity.getNotes(),
                entity.getOffRoute(),
                entity.getCreatedAt(),
                entity.getUpdatedAt());
    }
}
