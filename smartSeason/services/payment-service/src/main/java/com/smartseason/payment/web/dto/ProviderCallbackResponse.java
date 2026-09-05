package com.smartseason.payment.web.dto;

import com.smartseason.payment.domain.ProviderCallback;
import java.time.Instant;
import java.util.UUID;

public record ProviderCallbackResponse(
        UUID id,
        String provider,
        String callbackType,
        String externalRef,
        String signature,
        String payload,
        Instant receivedAt,
        Instant processedAt,
        ProviderCallback.Status status,
        String error,
        Instant createdAt,
        Instant updatedAt) {

    public static ProviderCallbackResponse from(ProviderCallback entity) {
        return new ProviderCallbackResponse(
                entity.getId(),
                entity.getProvider(),
                entity.getCallbackType(),
                entity.getExternalRef(),
                entity.getSignature(),
                entity.getPayload(),
                entity.getReceivedAt(),
                entity.getProcessedAt(),
                entity.getStatus(),
                entity.getError(),
                entity.getCreatedAt(),
                entity.getUpdatedAt());
    }
}
