package com.smartseason.logistics.web.dto;

import com.smartseason.logistics.domain.TransportJob;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record TransportJobCreateRequest(
        @NotBlank @Size(max = 255) String jobNumber,
        UUID orderId,
        UUID batchId,
        UUID vehicleId,
        UUID driverId,
        @Size(max = 255) String pickupCounty,
        BigDecimal pickupLat,
        BigDecimal pickupLng,
        Instant pickupAt,
        @Size(max = 255) String dropoffCounty,
        BigDecimal dropoffLat,
        BigDecimal dropoffLng,
        Instant dropoffAt,
        BigDecimal distanceKm,
        BigDecimal weightKg,
        BigDecimal freightCost,
        @Size(max = 255) String currency,
        @NotNull Boolean requiresColdChain,
        @NotNull TransportJob.Status status) {
}
