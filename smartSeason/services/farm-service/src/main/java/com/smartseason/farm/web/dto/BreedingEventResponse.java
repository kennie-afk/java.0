package com.smartseason.farm.web.dto;

import com.smartseason.farm.domain.BreedingEvent;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record BreedingEventResponse(
        UUID id,
        UUID cowId,
        UUID farmId,
        LocalDate eventDate,
        BreedingEvent.EventType eventType,
        BreedingEvent.Method method,
        String sireRef,
        String outcome,
        LocalDate expectedCalvingOn,
        String notes,
        Instant createdAt,
        Instant updatedAt) {

    public static BreedingEventResponse from(BreedingEvent entity) {
        return new BreedingEventResponse(
                entity.getId(),
                entity.getCowId(),
                entity.getFarmId(),
                entity.getEventDate(),
                entity.getEventType(),
                entity.getMethod(),
                entity.getSireRef(),
                entity.getOutcome(),
                entity.getExpectedCalvingOn(),
                entity.getNotes(),
                entity.getCreatedAt(),
                entity.getUpdatedAt());
    }
}
