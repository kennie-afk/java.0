package com.smartseason.task.web.dto;

import com.smartseason.task.domain.ChecklistItem;
import java.time.Instant;
import java.util.UUID;

public record ChecklistItemResponse(
        UUID id,
        UUID workOrderId,
        String label,
        Integer sequence,
        Boolean required,
        Boolean completed,
        Instant completedAt,
        UUID completedBy,
        Instant createdAt,
        Instant updatedAt) {

    public static ChecklistItemResponse from(ChecklistItem entity) {
        return new ChecklistItemResponse(
                entity.getId(),
                entity.getWorkOrderId(),
                entity.getLabel(),
                entity.getSequence(),
                entity.getRequired(),
                entity.getCompleted(),
                entity.getCompletedAt(),
                entity.getCompletedBy(),
                entity.getCreatedAt(),
                entity.getUpdatedAt());
    }
}
