package com.smartseason.workforce.web.dto;

import com.smartseason.workforce.domain.Gang;
import java.time.Instant;
import java.util.UUID;

public record GangResponse(
        UUID id,
        String name,
        UUID farmId,
        UUID supervisorId,
        Integer targetSize,
        Gang.Status status,
        String notes,
        Instant createdAt,
        Instant updatedAt) {

    public static GangResponse from(Gang entity) {
        return new GangResponse(
                entity.getId(),
                entity.getName(),
                entity.getFarmId(),
                entity.getSupervisorId(),
                entity.getTargetSize(),
                entity.getStatus(),
                entity.getNotes(),
                entity.getCreatedAt(),
                entity.getUpdatedAt());
    }
}
