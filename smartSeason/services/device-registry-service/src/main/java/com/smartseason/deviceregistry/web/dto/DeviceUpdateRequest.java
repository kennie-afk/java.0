package com.smartseason.deviceregistry.web.dto;

import com.smartseason.deviceregistry.domain.Device;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record DeviceUpdateRequest(
        @Size(max = 255) String serialNumber,
        Device.DeviceType deviceType,
        UUID plotId,
        UUID farmId,
        @Size(max = 255) String model,
        @Size(max = 255) String firmwareVersion,
        Device.Status status,
        Instant lastSeenAt,
        BigDecimal latitude,
        BigDecimal longitude) {
}
