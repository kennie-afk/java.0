package com.smartseason.attendance.web.dto;

import com.smartseason.attendance.domain.Shift;
import java.time.Instant;
import java.util.UUID;

public record ShiftResponse(
        UUID id,
        UUID workerId,
        UUID farmId,
        UUID gangId,
        Instant startedAt,
        Instant endedAt,
        Integer durationMinutes,
        Integer breakMinutes,
        UUID supervisorId,
        Shift.Status status,
        String anomalyFlags,
        Instant createdAt,
        Instant updatedAt) {

    public static ShiftResponse from(Shift entity) {
        return new ShiftResponse(
                entity.getId(),
                entity.getWorkerId(),
                entity.getFarmId(),
                entity.getGangId(),
                entity.getStartedAt(),
                entity.getEndedAt(),
                entity.getDurationMinutes(),
                entity.getBreakMinutes(),
                entity.getSupervisorId(),
                entity.getStatus(),
                entity.getAnomalyFlags(),
                entity.getCreatedAt(),
                entity.getUpdatedAt());
    }
}
