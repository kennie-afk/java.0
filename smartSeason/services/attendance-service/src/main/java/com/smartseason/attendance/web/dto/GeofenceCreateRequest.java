package com.smartseason.attendance.web.dto;

import com.smartseason.attendance.domain.Geofence;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.UUID;

public record GeofenceCreateRequest(
        @NotNull UUID farmId,
        UUID plotId,
        @NotBlank @Size(max = 255) String name,
        @NotNull BigDecimal centerLat,
        @NotNull BigDecimal centerLng,
        @NotNull Integer radiusM,
        @NotNull Boolean active) {
}
