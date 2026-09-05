package com.smartseason.automation.web.dto;

import com.smartseason.automation.domain.ActuatorCommand;
import java.time.Instant;
import java.util.UUID;

public record ActuatorCommandResponse(
        UUID id,
        UUID deviceId,
        UUID ruleId,
        String commandKey,
        String action,
        String payload,
        Instant issuedAt,
        Instant acknowledgedAt,
        Instant completedAt,
        ActuatorCommand.Status status,
        Integer attempts,
        String failureReason,
        Instant createdAt,
        Instant updatedAt) {

    public static ActuatorCommandResponse from(ActuatorCommand entity) {
        return new ActuatorCommandResponse(
                entity.getId(),
                entity.getDeviceId(),
                entity.getRuleId(),
                entity.getCommandKey(),
                entity.getAction(),
                entity.getPayload(),
                entity.getIssuedAt(),
                entity.getAcknowledgedAt(),
                entity.getCompletedAt(),
                entity.getStatus(),
                entity.getAttempts(),
                entity.getFailureReason(),
                entity.getCreatedAt(),
                entity.getUpdatedAt());
    }
}
