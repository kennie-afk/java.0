package com.smartseason.logistics.web.dto;

import com.smartseason.logistics.domain.TransportJob;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record TransportJobResponse(
        UUID id,
        String jobNumber,
        UUID orderId,
        UUID batchId,
        UUID vehicleId,
        UUID driverId,
        String pickupCounty,
        BigDecimal pickupLat,
        BigDecimal pickupLng,
        Instant pickupAt,
        String dropoffCounty,
        BigDecimal dropoffLat,
        BigDecimal dropoffLng,
        Instant dropoffAt,
        BigDecimal distanceKm,
        BigDecimal weightKg,
        BigDecimal freightCost,
        String currency,
        Boolean requiresColdChain,
        TransportJob.Status status,
        Instant createdAt,
        Instant updatedAt) {

    public static TransportJobResponse from(TransportJob entity) {
        return new TransportJobResponse(
                entity.getId(),
                entity.getJobNumber(),
                entity.getOrderId(),
                entity.getBatchId(),
                entity.getVehicleId(),
                entity.getDriverId(),
                entity.getPickupCounty(),
                entity.getPickupLat(),
                entity.getPickupLng(),
                entity.getPickupAt(),
                entity.getDropoffCounty(),
                entity.getDropoffLat(),
                entity.getDropoffLng(),
                entity.getDropoffAt(),
                entity.getDistanceKm(),
                entity.getWeightKg(),
                entity.getFreightCost(),
                entity.getCurrency(),
                entity.getRequiresColdChain(),
                entity.getStatus(),
                entity.getCreatedAt(),
                entity.getUpdatedAt());
    }
}
