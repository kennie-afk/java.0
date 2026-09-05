package com.smartseason.payment.web.dto;

import com.smartseason.payment.domain.ProviderCallback;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;

public record ProviderCallbackUpdateRequest(
        @Size(max = 255) String provider,
        @Size(max = 255) String callbackType,
        @Size(max = 255) String externalRef,
        @Size(max = 255) String signature,
        String payload,
        Instant receivedAt,
        Instant processedAt,
        ProviderCallback.Status status,
        String error) {
}
