package com.smartseason.task.web.dto;

import com.smartseason.task.domain.TaskAssignment;
import java.time.Instant;
import java.util.UUID;

public record TaskAssignmentResponse(
        UUID id,
        UUID workOrderId,
        UUID workerId,
        UUID gangId,
        UUID assignedBy,
        Instant assignedAt,
        Instant acceptedAt,
        Instant startedAt,
        Instant completedAt,
        TaskAssignment.Status status,
        Instant createdAt,
        Instant updatedAt) {

    public static TaskAssignmentResponse from(TaskAssignment entity) {
        return new TaskAssignmentResponse(
                entity.getId(),
                entity.getWorkOrderId(),
                entity.getWorkerId(),
                entity.getGangId(),
                entity.getAssignedBy(),
                entity.getAssignedAt(),
                entity.getAcceptedAt(),
                entity.getStartedAt(),
                entity.getCompletedAt(),
                entity.getStatus(),
                entity.getCreatedAt(),
                entity.getUpdatedAt());
    }
}
