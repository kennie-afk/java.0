package com.smartseason.order.web.dto;

import com.smartseason.order.domain.OrderSagaState;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;

public record OrderSagaStateUpdateRequest(
        UUID orderId,
        @Size(max = 255) String currentStep,
        OrderSagaState.StepStatus stepStatus,
        Integer attempts,
        String lastError,
        Instant startedAt,
        Instant lastTransitionAt,
        Instant completedAt,
        String context) {
}
