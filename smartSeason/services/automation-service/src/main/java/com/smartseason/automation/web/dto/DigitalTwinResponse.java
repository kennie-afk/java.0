package com.smartseason.automation.web.dto;

import com.smartseason.automation.domain.DigitalTwin;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record DigitalTwinResponse(
        UUID id,
        UUID plotId,
        BigDecimal soilMoisturePct,
        BigDecimal soilTempC,
        BigDecimal canopyIndex,
        DigitalTwin.IrrigationState irrigationState,
        Instant lastIrrigatedAt,
        Instant updatedFromEventAt,
        String state,
        Instant createdAt,
        Instant updatedAt) {

    public static DigitalTwinResponse from(DigitalTwin entity) {
        return new DigitalTwinResponse(
                entity.getId(),
                entity.getPlotId(),
                entity.getSoilMoisturePct(),
                entity.getSoilTempC(),
                entity.getCanopyIndex(),
                entity.getIrrigationState(),
                entity.getLastIrrigatedAt(),
                entity.getUpdatedFromEventAt(),
                entity.getState(),
                entity.getCreatedAt(),
                entity.getUpdatedAt());
    }
}
