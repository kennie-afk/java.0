package com.smartseason.automation.web.dto;

import com.smartseason.automation.domain.SafetyInterlock;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;

public record SafetyInterlockCreateRequest(
        @NotNull UUID deviceId,
        @NotNull SafetyInterlock.InterlockType interlockType,
        Integer maxRuntimeSeconds,
        UUID conflictingDeviceId,
        @NotNull Boolean engaged,
        Instant engagedAt,
        @Size(max = 255) String reason) {
}
