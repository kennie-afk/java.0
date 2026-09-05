package com.smartseason.logistics.web.dto;

import com.smartseason.logistics.domain.Driver;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record DriverUpdateRequest(
        UUID userId,
        @Size(max = 255) String fullName,
        @Size(max = 255) String phone,
        @Size(max = 255) String licenceNumber,
        LocalDate licenceExpiry,
        UUID assignedVehicleId,
        BigDecimal rating,
        Driver.Status status) {
}
