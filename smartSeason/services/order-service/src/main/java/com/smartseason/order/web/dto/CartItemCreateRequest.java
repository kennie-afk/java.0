package com.smartseason.order.web.dto;

import com.smartseason.order.domain.CartItem;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.UUID;

public record CartItemCreateRequest(
        @NotNull UUID cartId,
        @NotNull UUID listingId,
        @NotBlank @Size(max = 255) String commodityCode,
        @NotNull BigDecimal quantity,
        @NotBlank @Size(max = 255) String unit,
        @NotNull BigDecimal unitPrice,
        @NotNull UUID sellerOrgId) {
}
