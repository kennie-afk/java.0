package com.smartseason.order.web.dto;

import com.smartseason.order.domain.CartItem;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record CartItemResponse(
        UUID id,
        UUID cartId,
        UUID listingId,
        String commodityCode,
        BigDecimal quantity,
        String unit,
        BigDecimal unitPrice,
        UUID sellerOrgId,
        Instant createdAt,
        Instant updatedAt) {

    public static CartItemResponse from(CartItem entity) {
        return new CartItemResponse(
                entity.getId(),
                entity.getCartId(),
                entity.getListingId(),
                entity.getCommodityCode(),
                entity.getQuantity(),
                entity.getUnit(),
                entity.getUnitPrice(),
                entity.getSellerOrgId(),
                entity.getCreatedAt(),
                entity.getUpdatedAt());
    }
}
