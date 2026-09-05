package com.smartseason.inventory.web.dto;

import com.smartseason.inventory.domain.Reservation;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record ReservationCreateRequest(
        @NotNull UUID stockItemId,
        UUID orderId,
        @NotNull BigDecimal quantity,
        @NotNull Instant reservedAt,
        Instant expiresAt,
        Instant releasedAt,
        @NotNull Reservation.Status status) {
}
