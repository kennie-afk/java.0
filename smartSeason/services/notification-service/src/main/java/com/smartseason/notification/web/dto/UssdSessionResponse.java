package com.smartseason.notification.web.dto;

import com.smartseason.notification.domain.UssdSession;
import java.time.Instant;
import java.util.UUID;

public record UssdSessionResponse(
        UUID id,
        String sessionId,
        String phoneNumber,
        String serviceCode,
        String currentMenu,
        String menuStack,
        String context,
        Instant startedAt,
        Instant lastInputAt,
        Instant endedAt,
        UssdSession.Status status,
        Integer hops,
        Instant createdAt,
        Instant updatedAt) {

    public static UssdSessionResponse from(UssdSession entity) {
        return new UssdSessionResponse(
                entity.getId(),
                entity.getSessionId(),
                entity.getPhoneNumber(),
                entity.getServiceCode(),
                entity.getCurrentMenu(),
                entity.getMenuStack(),
                entity.getContext(),
                entity.getStartedAt(),
                entity.getLastInputAt(),
                entity.getEndedAt(),
                entity.getStatus(),
                entity.getHops(),
                entity.getCreatedAt(),
                entity.getUpdatedAt());
    }
}
