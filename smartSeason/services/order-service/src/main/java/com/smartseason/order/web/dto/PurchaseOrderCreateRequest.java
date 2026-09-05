package com.smartseason.order.web.dto;

import com.smartseason.order.domain.PurchaseOrder;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record PurchaseOrderCreateRequest(
        @NotBlank @Size(max = 255) String orderNumber,
        @NotNull UUID buyerOrgId,
        @NotNull UUID sellerOrgId,
        @NotBlank @Size(max = 255) String currency,
        @NotNull BigDecimal subtotal,
        @NotNull BigDecimal deliveryFee,
        @NotNull BigDecimal platformFee,
        @NotNull BigDecimal totalAmount,
        @NotNull Instant placedAt,
        Instant confirmedAt,
        Instant fulfilledAt,
        Instant cancelledAt,
        @Size(max = 255) String cancellationReason,
        @Size(max = 255) String deliveryCounty,
        String deliveryAddress,
        BigDecimal deliveryLat,
        BigDecimal deliveryLng,
        UUID paymentIntentId,
        UUID transportJobId,
        @NotNull PurchaseOrder.Status status,
        @Size(max = 255) String idempotencyKey) {
}
