package com.smartseason.order.web.dto;

import com.smartseason.order.domain.Cart;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;

public record CartUpdateRequest(
        UUID buyerOrgId,
        UUID buyerUserId,
        @Size(max = 255) String currency,
        Cart.Status status,
        Instant expiresAt) {
}
