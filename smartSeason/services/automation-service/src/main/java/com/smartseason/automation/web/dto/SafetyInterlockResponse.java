package com.smartseason.automation.web.dto;

import com.smartseason.automation.domain.SafetyInterlock;
import java.time.Instant;
import java.util.UUID;

public record SafetyInterlockResponse(
        UUID id,
        UUID deviceId,
        SafetyInterlock.InterlockType interlockType,
        Integer maxRuntimeSeconds,
        UUID conflictingDeviceId,
        Boolean engaged,
        Instant engagedAt,
        String reason,
        Instant createdAt,
        Instant updatedAt) {

    public static SafetyInterlockResponse from(SafetyInterlock entity) {
        return new SafetyInterlockResponse(
                entity.getId(),
                entity.getDeviceId(),
                entity.getInterlockType(),
                entity.getMaxRuntimeSeconds(),
                entity.getConflictingDeviceId(),
                entity.getEngaged(),
                entity.getEngagedAt(),
                entity.getReason(),
                entity.getCreatedAt(),
                entity.getUpdatedAt());
    }
}
