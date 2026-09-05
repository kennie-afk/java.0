package com.smartseason.notification.web.dto;

import com.smartseason.notification.domain.UssdSession;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;

public record UssdSessionUpdateRequest(
        @Size(max = 255) String sessionId,
        @Size(max = 255) String phoneNumber,
        @Size(max = 255) String serviceCode,
        @Size(max = 255) String currentMenu,
        @Size(max = 255) String menuStack,
        String context,
        Instant startedAt,
        Instant lastInputAt,
        Instant endedAt,
        UssdSession.Status status,
        Integer hops) {
}
