package com.smartseason.task.web.dto;

import com.smartseason.task.domain.ChecklistItem;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;

public record ChecklistItemCreateRequest(
        @NotNull UUID workOrderId,
        @NotBlank @Size(max = 255) String label,
        @NotNull Integer sequence,
        @NotNull Boolean required,
        @NotNull Boolean completed,
        Instant completedAt,
        UUID completedBy) {
}
