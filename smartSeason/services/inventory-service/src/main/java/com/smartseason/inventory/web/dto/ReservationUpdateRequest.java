package com.smartseason.inventory.web.dto;

import com.smartseason.inventory.domain.Reservation;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record ReservationUpdateRequest(
        UUID stockItemId,
        UUID orderId,
        BigDecimal quantity,
        Instant reservedAt,
        Instant expiresAt,
        Instant releasedAt,
        Reservation.Status status) {
}
