package com.smartseason.notification.web.dto;

import com.smartseason.notification.domain.UssdSession;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;

public record UssdSessionCreateRequest(
        @NotBlank @Size(max = 255) String sessionId,
        @NotBlank @Size(max = 255) String phoneNumber,
        @Size(max = 255) String serviceCode,
        @NotBlank @Size(max = 255) String currentMenu,
        @Size(max = 255) String menuStack,
        String context,
        @NotNull Instant startedAt,
        Instant lastInputAt,
        Instant endedAt,
        @NotNull UssdSession.Status status,
        @NotNull Integer hops) {
}
