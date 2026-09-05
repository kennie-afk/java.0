package com.smartseason.notification.web.dto;

import com.smartseason.notification.domain.Notification;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;

public record NotificationUpdateRequest(
        UUID recipientUserId,
        @Size(max = 255) String recipientPhone,
        @Size(max = 255) String recipientEmail,
        Notification.Channel channel,
        @Size(max = 255) String templateCode,
        @Size(max = 255) String locale,
        @Size(max = 255) String subject,
        String body,
        String payload,
        Notification.Priority priority,
        Instant scheduledFor,
        Instant sentAt,
        Instant deliveredAt,
        Instant failedAt,
        @Size(max = 255) String failureReason,
        @Size(max = 255) String providerRef,
        Integer attempts,
        Notification.Status status,
        @Size(max = 255) String idempotencyKey) {
}
