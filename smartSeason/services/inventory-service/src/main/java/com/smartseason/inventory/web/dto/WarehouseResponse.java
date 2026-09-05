package com.smartseason.inventory.web.dto;

import com.smartseason.inventory.domain.Warehouse;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record WarehouseResponse(
        UUID id,
        String name,
        String county,
        BigDecimal latitude,
        BigDecimal longitude,
        BigDecimal capacityKg,
        Boolean coldChain,
        UUID managerUserId,
        Warehouse.Status status,
        Instant createdAt,
        Instant updatedAt) {

    public static WarehouseResponse from(Warehouse entity) {
        return new WarehouseResponse(
                entity.getId(),
                entity.getName(),
                entity.getCounty(),
                entity.getLatitude(),
                entity.getLongitude(),
                entity.getCapacityKg(),
                entity.getColdChain(),
                entity.getManagerUserId(),
                entity.getStatus(),
                entity.getCreatedAt(),
                entity.getUpdatedAt());
    }
}
