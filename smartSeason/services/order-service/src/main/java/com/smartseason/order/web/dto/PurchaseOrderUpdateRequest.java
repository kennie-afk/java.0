package com.smartseason.order.web.dto;

import com.smartseason.order.domain.PurchaseOrder;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record PurchaseOrderUpdateRequest(
        @Size(max = 255) String orderNumber,
        UUID buyerOrgId,
        UUID sellerOrgId,
        @Size(max = 255) String currency,
        BigDecimal subtotal,
        BigDecimal deliveryFee,
        BigDecimal platformFee,
        BigDecimal totalAmount,
        Instant placedAt,
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
        PurchaseOrder.Status status,
        @Size(max = 255) String idempotencyKey) {
}
