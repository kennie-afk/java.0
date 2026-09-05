package com.smartseason.inventory.stock;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.UUID;

public record ReservationRequest(
        @NotNull UUID stockItemId,
        @NotNull UUID orderId,
        @NotNull @DecimalMin("0.0001") BigDecimal quantity,
        Integer holdMinutes) {
}
