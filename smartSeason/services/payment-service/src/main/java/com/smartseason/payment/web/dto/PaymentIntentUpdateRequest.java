package com.smartseason.payment.web.dto;

import com.smartseason.payment.domain.PaymentIntent;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record PaymentIntentUpdateRequest(
        @Size(max = 255) String reference,
        UUID orderId,
        UUID payerOrgId,
        UUID payeeOrgId,
        BigDecimal amount,
        @Size(max = 255) String currency,
        PaymentIntent.Method method,
        PaymentIntent.Purpose purpose,
        @Size(max = 255) String payerPhone,
        PaymentIntent.Status status,
        @Size(max = 255) String idempotencyKey,
        Instant initiatedAt,
        Instant completedAt,
        @Size(max = 255) String failureReason,
        @Size(max = 255) String providerRef,
        Boolean escrow) {
}
