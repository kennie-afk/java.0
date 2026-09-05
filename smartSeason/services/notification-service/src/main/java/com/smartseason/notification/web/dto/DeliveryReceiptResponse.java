package com.smartseason.notification.web.dto;

import com.smartseason.notification.domain.DeliveryReceipt;
import java.time.Instant;
import java.util.UUID;

public record DeliveryReceiptResponse(
        UUID id,
        UUID notificationId,
        String provider,
        String providerRef,
        String statusCode,
        String statusText,
        Instant receivedAt,
        String raw,
        Instant createdAt,
        Instant updatedAt) {

    public static DeliveryReceiptResponse from(DeliveryReceipt entity) {
        return new DeliveryReceiptResponse(
                entity.getId(),
                entity.getNotificationId(),
                entity.getProvider(),
                entity.getProviderRef(),
                entity.getStatusCode(),
                entity.getStatusText(),
                entity.getReceivedAt(),
                entity.getRaw(),
                entity.getCreatedAt(),
                entity.getUpdatedAt());
    }
}
