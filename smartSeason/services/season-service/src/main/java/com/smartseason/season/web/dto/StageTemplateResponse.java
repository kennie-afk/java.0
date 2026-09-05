package com.smartseason.season.web.dto;

import com.smartseason.season.domain.StageTemplate;
import java.time.Instant;
import java.util.UUID;

public record StageTemplateResponse(
        UUID id,
        String cropCode,
        String stageName,
        Integer sequence,
        Integer durationDays,
        String description,
        String keyActivities,
        Instant createdAt,
        Instant updatedAt) {

    public static StageTemplateResponse from(StageTemplate entity) {
        return new StageTemplateResponse(
                entity.getId(),
                entity.getCropCode(),
                entity.getStageName(),
                entity.getSequence(),
                entity.getDurationDays(),
                entity.getDescription(),
                entity.getKeyActivities(),
                entity.getCreatedAt(),
                entity.getUpdatedAt());
    }
}
