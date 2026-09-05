package com.smartseason.deviceregistry.web.dto;

import com.smartseason.deviceregistry.domain.Device;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record DeviceResponse(
        UUID id,
        String serialNumber,
        Device.DeviceType deviceType,
        UUID plotId,
        UUID farmId,
        String model,
        String firmwareVersion,
        Device.Status status,
        Instant lastSeenAt,
        BigDecimal latitude,
        BigDecimal longitude,
        Instant createdAt,
        Instant updatedAt) {

    public static DeviceResponse from(Device entity) {
        return new DeviceResponse(
                entity.getId(),
                entity.getSerialNumber(),
                entity.getDeviceType(),
                entity.getPlotId(),
                entity.getFarmId(),
                entity.getModel(),
                entity.getFirmwareVersion(),
                entity.getStatus(),
                entity.getLastSeenAt(),
                entity.getLatitude(),
                entity.getLongitude(),
                entity.getCreatedAt(),
                entity.getUpdatedAt());
    }
}
