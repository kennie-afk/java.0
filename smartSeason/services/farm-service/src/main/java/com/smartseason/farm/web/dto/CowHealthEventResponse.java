package com.smartseason.farm.web.dto;

import com.smartseason.farm.domain.CowHealthEvent;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record CowHealthEventResponse(
        UUID id,
        UUID cowId,
        UUID farmId,
        LocalDate eventDate,
        CowHealthEvent.EventType eventType,
        String description,
        String medicine,
        LocalDate withdrawalEndsOn,
        String vetName,
        BigDecimal costAmount,
        Instant createdAt,
        Instant updatedAt) {

    public static CowHealthEventResponse from(CowHealthEvent entity) {
        return new CowHealthEventResponse(
                entity.getId(),
                entity.getCowId(),
                entity.getFarmId(),
                entity.getEventDate(),
                entity.getEventType(),
                entity.getDescription(),
                entity.getMedicine(),
                entity.getWithdrawalEndsOn(),
                entity.getVetName(),
                entity.getCostAmount(),
                entity.getCreatedAt(),
                entity.getUpdatedAt());
    }
}
