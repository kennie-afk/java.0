package com.smartseason.order.web.dto;

import com.smartseason.order.domain.PurchaseOrder;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record PurchaseOrderResponse(
        UUID id,
        String orderNumber,
        UUID buyerOrgId,
        UUID sellerOrgId,
        String currency,
        BigDecimal subtotal,
        BigDecimal deliveryFee,
        BigDecimal platformFee,
        BigDecimal totalAmount,
        Instant placedAt,
        Instant confirmedAt,
        Instant fulfilledAt,
        Instant cancelledAt,
        String cancellationReason,
        String deliveryCounty,
        String deliveryAddress,
        BigDecimal deliveryLat,
        BigDecimal deliveryLng,
        UUID paymentIntentId,
        UUID transportJobId,
        PurchaseOrder.Status status,
        String idempotencyKey,
        Instant createdAt,
        Instant updatedAt) {

    public static PurchaseOrderResponse from(PurchaseOrder entity) {
        return new PurchaseOrderResponse(
                entity.getId(),
                entity.getOrderNumber(),
                entity.getBuyerOrgId(),
                entity.getSellerOrgId(),
                entity.getCurrency(),
                entity.getSubtotal(),
                entity.getDeliveryFee(),
                entity.getPlatformFee(),
                entity.getTotalAmount(),
                entity.getPlacedAt(),
                entity.getConfirmedAt(),
                entity.getFulfilledAt(),
                entity.getCancelledAt(),
                entity.getCancellationReason(),
                entity.getDeliveryCounty(),
                entity.getDeliveryAddress(),
                entity.getDeliveryLat(),
                entity.getDeliveryLng(),
                entity.getPaymentIntentId(),
                entity.getTransportJobId(),
                entity.getStatus(),
                entity.getIdempotencyKey(),
                entity.getCreatedAt(),
                entity.getUpdatedAt());
    }
}
