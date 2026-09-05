package com.smartseason.payment.web.dto;

import com.smartseason.payment.domain.ProviderCallback;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;

public record ProviderCallbackCreateRequest(
        @NotBlank @Size(max = 255) String provider,
        @NotBlank @Size(max = 255) String callbackType,
        @Size(max = 255) String externalRef,
        @Size(max = 255) String signature,
        @NotBlank String payload,
        @NotNull Instant receivedAt,
        Instant processedAt,
        @NotNull ProviderCallback.Status status,
        String error) {
}
