package com.smartseason.logistics.web.dto;

import com.smartseason.logistics.domain.Driver;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record DriverResponse(
        UUID id,
        UUID userId,
        String fullName,
        String phone,
        String licenceNumber,
        LocalDate licenceExpiry,
        UUID assignedVehicleId,
        BigDecimal rating,
        Driver.Status status,
        Instant createdAt,
        Instant updatedAt) {

    public static DriverResponse from(Driver entity) {
        return new DriverResponse(
                entity.getId(),
                entity.getUserId(),
                entity.getFullName(),
                entity.getPhone(),
                entity.getLicenceNumber(),
                entity.getLicenceExpiry(),
                entity.getAssignedVehicleId(),
                entity.getRating(),
                entity.getStatus(),
                entity.getCreatedAt(),
                entity.getUpdatedAt());
    }
}
