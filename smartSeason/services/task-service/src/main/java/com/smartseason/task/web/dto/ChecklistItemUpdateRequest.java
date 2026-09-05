package com.smartseason.task.web.dto;

import com.smartseason.task.domain.ChecklistItem;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;

public record ChecklistItemUpdateRequest(
        UUID workOrderId,
        @Size(max = 255) String label,
        Integer sequence,
        Boolean required,
        Boolean completed,
        Instant completedAt,
        UUID completedBy) {
}
