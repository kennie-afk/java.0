package com.smartseason.order.web.dto;

import com.smartseason.order.domain.Cart;
import java.time.Instant;
import java.util.UUID;

public record CartResponse(
        UUID id,
        UUID buyerOrgId,
        UUID buyerUserId,
        String currency,
        Cart.Status status,
        Instant expiresAt,
        Instant createdAt,
        Instant updatedAt) {

    public static CartResponse from(Cart entity) {
        return new CartResponse(
                entity.getId(),
                entity.getBuyerOrgId(),
                entity.getBuyerUserId(),
                entity.getCurrency(),
                entity.getStatus(),
                entity.getExpiresAt(),
                entity.getCreatedAt(),
                entity.getUpdatedAt());
    }
}
