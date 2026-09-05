package com.smartseason.automation.web.dto;

import com.smartseason.automation.domain.DigitalTwin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record DigitalTwinUpdateRequest(
        UUID plotId,
        BigDecimal soilMoisturePct,
        BigDecimal soilTempC,
        BigDecimal canopyIndex,
        DigitalTwin.IrrigationState irrigationState,
        Instant lastIrrigatedAt,
        Instant updatedFromEventAt,
        String state) {
}
