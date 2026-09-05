package com.smartseason.season.web.dto;

import com.smartseason.season.domain.SeasonStage;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record SeasonStageResponse(
        UUID id,
        UUID seasonId,
        String stageName,
        Integer sequence,
        LocalDate plannedStart,
        LocalDate plannedEnd,
        LocalDate actualStart,
        LocalDate actualEnd,
        SeasonStage.Status status,
        String notes,
        Instant createdAt,
        Instant updatedAt) {

    public static SeasonStageResponse from(SeasonStage entity) {
        return new SeasonStageResponse(
                entity.getId(),
                entity.getSeasonId(),
                entity.getStageName(),
                entity.getSequence(),
                entity.getPlannedStart(),
                entity.getPlannedEnd(),
                entity.getActualStart(),
                entity.getActualEnd(),
                entity.getStatus(),
                entity.getNotes(),
                entity.getCreatedAt(),
                entity.getUpdatedAt());
    }
}
