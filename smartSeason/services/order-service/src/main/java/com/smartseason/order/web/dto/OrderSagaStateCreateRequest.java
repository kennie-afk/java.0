package com.smartseason.order.web.dto;

import com.smartseason.order.domain.OrderSagaState;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;

public record OrderSagaStateCreateRequest(
        @NotNull UUID orderId,
        @NotBlank @Size(max = 255) String currentStep,
        @NotNull OrderSagaState.StepStatus stepStatus,
        @NotNull Integer attempts,
        String lastError,
        @NotNull Instant startedAt,
        Instant lastTransitionAt,
        Instant completedAt,
        String context) {
}
