package com.smartseason.order.web.dto;

import com.smartseason.order.domain.OrderSagaState;
import java.time.Instant;
import java.util.UUID;

public record OrderSagaStateResponse(
        UUID id,
        UUID orderId,
        String currentStep,
        OrderSagaState.StepStatus stepStatus,
        Integer attempts,
        String lastError,
        Instant startedAt,
        Instant lastTransitionAt,
        Instant completedAt,
        String context,
        Instant createdAt,
        Instant updatedAt) {

    public static OrderSagaStateResponse from(OrderSagaState entity) {
        return new OrderSagaStateResponse(
                entity.getId(),
                entity.getOrderId(),
                entity.getCurrentStep(),
                entity.getStepStatus(),
                entity.getAttempts(),
                entity.getLastError(),
                entity.getStartedAt(),
                entity.getLastTransitionAt(),
                entity.getCompletedAt(),
                entity.getContext(),
                entity.getCreatedAt(),
                entity.getUpdatedAt());
    }
}
