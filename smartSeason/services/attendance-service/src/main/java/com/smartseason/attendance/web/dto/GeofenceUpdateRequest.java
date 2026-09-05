package com.smartseason.attendance.web.dto;

import com.smartseason.attendance.domain.Geofence;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.UUID;

public record GeofenceUpdateRequest(
        UUID farmId,
        UUID plotId,
        @Size(max = 255) String name,
        BigDecimal centerLat,
        BigDecimal centerLng,
        Integer radiusM,
        Boolean active) {
}
