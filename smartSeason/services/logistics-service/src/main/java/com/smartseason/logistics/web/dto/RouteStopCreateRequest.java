package com.smartseason.logistics.web.dto;

import com.smartseason.logistics.domain.RouteStop;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record RouteStopCreateRequest(
        @NotNull UUID transportJobId,
        @NotNull Integer sequence,
        @NotNull RouteStop.StopType stopType,
        BigDecimal latitude,
        BigDecimal longitude,
        Instant plannedAt,
        Instant arrivedAt,
        Instant departedAt,
        String notes,
        @NotNull Boolean offRoute) {
}
