package com.smartseason.task.web.dto;

import com.smartseason.task.domain.TaskAssignment;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;

public record TaskAssignmentUpdateRequest(
        UUID workOrderId,
        UUID workerId,
        UUID workerUserId,
        UUID gangId,
        UUID assignedBy,
        Instant assignedAt,
        Instant acceptedAt,
        Instant startedAt,
        Instant completedAt,
        TaskAssignment.Status status) {
}
