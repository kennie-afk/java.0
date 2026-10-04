package com.smartseason.farm.web.dto;

import com.smartseason.farm.domain.Cow;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record CowResponse(
        UUID id,
        UUID farmId,
        String tagNo,
        String name,
        String breed,
        Cow.Sex sex,
        LocalDate birthDate,
        UUID damId,
        String sireRef,
        Cow.Status status,
        LocalDate acquiredOn,
        LocalDate exitedOn,
        String notes,
        Instant createdAt,
        Instant updatedAt) {

    public static CowResponse from(Cow entity) {
        return new CowResponse(
                entity.getId(),
                entity.getFarmId(),
                entity.getTagNo(),
                entity.getName(),
                entity.getBreed(),
                entity.getSex(),
                entity.getBirthDate(),
                entity.getDamId(),
                entity.getSireRef(),
                entity.getStatus(),
                entity.getAcquiredOn(),
                entity.getExitedOn(),
                entity.getNotes(),
                entity.getCreatedAt(),
                entity.getUpdatedAt());
    }
}
