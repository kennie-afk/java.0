package com.smartseason.payment.web.dto;

import com.smartseason.payment.domain.PaymentIntent;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record PaymentIntentResponse(
        UUID id,
        String reference,
        UUID orderId,
        UUID payerOrgId,
        UUID payeeOrgId,
        BigDecimal amount,
        String currency,
        PaymentIntent.Method method,
        PaymentIntent.Purpose purpose,
        String payerPhone,
        PaymentIntent.Status status,
        String idempotencyKey,
        Instant initiatedAt,
        Instant completedAt,
        String failureReason,
        String providerRef,
        Boolean escrow,
        Instant createdAt,
        Instant updatedAt) {

    public static PaymentIntentResponse from(PaymentIntent entity) {
        return new PaymentIntentResponse(
                entity.getId(),
                entity.getReference(),
                entity.getOrderId(),
                entity.getPayerOrgId(),
                entity.getPayeeOrgId(),
                entity.getAmount(),
                entity.getCurrency(),
                entity.getMethod(),
                entity.getPurpose(),
                entity.getPayerPhone(),
                entity.getStatus(),
                entity.getIdempotencyKey(),
                entity.getInitiatedAt(),
                entity.getCompletedAt(),
                entity.getFailureReason(),
                entity.getProviderRef(),
                entity.getEscrow(),
                entity.getCreatedAt(),
                entity.getUpdatedAt());
    }
}
