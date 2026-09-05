package com.smartseason.notification.web.dto;

import com.smartseason.notification.domain.DeliveryReceipt;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;

public record DeliveryReceiptCreateRequest(
        @NotNull UUID notificationId,
        @NotBlank @Size(max = 255) String provider,
        @Size(max = 255) String providerRef,
        @Size(max = 255) String statusCode,
        @Size(max = 255) String statusText,
        @NotNull Instant receivedAt,
        String raw) {
}
