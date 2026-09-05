package com.smartseason.inventory.web.dto;

import com.smartseason.inventory.domain.Reservation;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record ReservationResponse(
        UUID id,
        UUID stockItemId,
        UUID orderId,
        BigDecimal quantity,
        Instant reservedAt,
        Instant expiresAt,
        Instant releasedAt,
        Reservation.Status status,
        Instant createdAt,
        Instant updatedAt) {

    public static ReservationResponse from(Reservation entity) {
        return new ReservationResponse(
                entity.getId(),
                entity.getStockItemId(),
                entity.getOrderId(),
                entity.getQuantity(),
                entity.getReservedAt(),
                entity.getExpiresAt(),
                entity.getReleasedAt(),
                entity.getStatus(),
                entity.getCreatedAt(),
                entity.getUpdatedAt());
    }
}
