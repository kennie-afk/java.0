package com.smartseason.notification.web.dto;

import com.smartseason.notification.domain.Notification;
import java.time.Instant;
import java.util.UUID;

public record NotificationResponse(
        UUID id,
        UUID recipientUserId,
        String recipientPhone,
        String recipientEmail,
        Notification.Channel channel,
        String templateCode,
        String locale,
        String subject,
        String body,
        String payload,
        Notification.Priority priority,
        Instant scheduledFor,
        Instant sentAt,
        Instant deliveredAt,
        Instant failedAt,
        String failureReason,
        String providerRef,
        Integer attempts,
        Notification.Status status,
        String idempotencyKey,
        Instant createdAt,
        Instant updatedAt) {

    public static NotificationResponse from(Notification entity) {
        return new NotificationResponse(
                entity.getId(),
                entity.getRecipientUserId(),
                entity.getRecipientPhone(),
                entity.getRecipientEmail(),
                entity.getChannel(),
                entity.getTemplateCode(),
                entity.getLocale(),
                entity.getSubject(),
                entity.getBody(),
                entity.getPayload(),
                entity.getPriority(),
                entity.getScheduledFor(),
                entity.getSentAt(),
                entity.getDeliveredAt(),
                entity.getFailedAt(),
                entity.getFailureReason(),
                entity.getProviderRef(),
                entity.getAttempts(),
                entity.getStatus(),
                entity.getIdempotencyKey(),
                entity.getCreatedAt(),
                entity.getUpdatedAt());
    }
}
