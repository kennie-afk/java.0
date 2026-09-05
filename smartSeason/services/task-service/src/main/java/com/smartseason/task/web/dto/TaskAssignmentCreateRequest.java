package com.smartseason.task.web.dto;

import com.smartseason.task.domain.TaskAssignment;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;

public record TaskAssignmentCreateRequest(
        @NotNull UUID workOrderId,
        UUID workerId,
        UUID gangId,
        @NotNull UUID assignedBy,
        @NotNull Instant assignedAt,
        Instant acceptedAt,
        Instant startedAt,
        Instant completedAt,
        @NotNull TaskAssignment.Status status) {
}
