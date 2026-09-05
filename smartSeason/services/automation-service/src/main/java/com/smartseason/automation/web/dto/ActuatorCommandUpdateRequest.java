package com.smartseason.automation.web.dto;

import com.smartseason.automation.domain.ActuatorCommand;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;

public record ActuatorCommandUpdateRequest(
        UUID deviceId,
        UUID ruleId,
        @Size(max = 255) String commandKey,
        @Size(max = 255) String action,
        String payload,
        Instant issuedAt,
        Instant acknowledgedAt,
        Instant completedAt,
        ActuatorCommand.Status status,
        Integer attempts,
        @Size(max = 255) String failureReason) {
}
