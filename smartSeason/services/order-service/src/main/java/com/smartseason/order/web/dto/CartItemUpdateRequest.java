package com.smartseason.order.web.dto;

import com.smartseason.order.domain.CartItem;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.UUID;

public record CartItemUpdateRequest(
        UUID cartId,
        UUID listingId,
        @Size(max = 255) String commodityCode,
        BigDecimal quantity,
        @Size(max = 255) String unit,
        BigDecimal unitPrice,
        UUID sellerOrgId) {
}
