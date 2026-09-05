package com.smartseason.automation.web.dto;

import com.smartseason.automation.domain.ActuatorCommand;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;

public record ActuatorCommandCreateRequest(
        @NotNull UUID deviceId,
        UUID ruleId,
        @NotBlank @Size(max = 255) String commandKey,
        @NotBlank @Size(max = 255) String action,
        String payload,
        @NotNull Instant issuedAt,
        Instant acknowledgedAt,
        Instant completedAt,
        @NotNull ActuatorCommand.Status status,
        @NotNull Integer attempts,
        @Size(max = 255) String failureReason) {
}
