package com.smartseason.order.web.dto;

import com.smartseason.order.domain.Cart;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;

public record CartCreateRequest(
        @NotNull UUID buyerOrgId,
        UUID buyerUserId,
        @NotBlank @Size(max = 255) String currency,
        @NotNull Cart.Status status,
        Instant expiresAt) {
}
