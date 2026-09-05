package com.smartseason.payment.web.dto;

import com.smartseason.payment.domain.PaymentIntent;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record PaymentIntentCreateRequest(
        @NotBlank @Size(max = 255) String reference,
        UUID orderId,
        UUID payerOrgId,
        UUID payeeOrgId,
        @NotNull BigDecimal amount,
        @NotBlank @Size(max = 255) String currency,
        @NotNull PaymentIntent.Method method,
        @NotNull PaymentIntent.Purpose purpose,
        @Size(max = 255) String payerPhone,
        @NotNull PaymentIntent.Status status,
        @NotBlank @Size(max = 255) String idempotencyKey,
        @NotNull Instant initiatedAt,
        Instant completedAt,
        @Size(max = 255) String failureReason,
        @Size(max = 255) String providerRef,
        @NotNull Boolean escrow) {
}
