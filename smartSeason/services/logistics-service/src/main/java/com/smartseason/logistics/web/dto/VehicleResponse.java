package com.smartseason.logistics.web.dto;

import com.smartseason.logistics.domain.Vehicle;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record VehicleResponse(
        UUID id,
        String registrationNo,
        Vehicle.VehicleType vehicleType,
        BigDecimal capacityKg,
        Boolean coldChain,
        UUID ownerOrgId,
        BigDecimal odometerKm,
        Vehicle.Status status,
        LocalDate lastServiceAt,
        Instant createdAt,
        Instant updatedAt) {

    public static VehicleResponse from(Vehicle entity) {
        return new VehicleResponse(
                entity.getId(),
                entity.getRegistrationNo(),
                entity.getVehicleType(),
                entity.getCapacityKg(),
                entity.getColdChain(),
                entity.getOwnerOrgId(),
                entity.getOdometerKm(),
                entity.getStatus(),
                entity.getLastServiceAt(),
                entity.getCreatedAt(),
                entity.getUpdatedAt());
    }
}
