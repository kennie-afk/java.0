package com.smartseason.logistics.web.dto;

import com.smartseason.logistics.domain.Vehicle;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record VehicleCreateRequest(
        @NotBlank @Size(max = 255) String registrationNo,
        @NotNull Vehicle.VehicleType vehicleType,
        BigDecimal capacityKg,
        @NotNull Boolean coldChain,
        UUID ownerOrgId,
        BigDecimal odometerKm,
        @NotNull Vehicle.Status status,
        LocalDate lastServiceAt) {
}
