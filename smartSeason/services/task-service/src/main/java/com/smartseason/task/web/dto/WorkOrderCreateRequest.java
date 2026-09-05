package com.smartseason.task.web.dto;

import com.smartseason.task.domain.WorkOrder;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record WorkOrderCreateRequest(
        @NotNull UUID farmId,
        UUID plotId,
        UUID seasonId,
        @NotBlank @Size(max = 255) String taskCode,
        @NotBlank @Size(max = 255) String title,
        String description,
        LocalDate dueDate,
        @NotNull WorkOrder.Priority priority,
        BigDecimal estimatedHours,
        UUID createdBy,
        @NotNull WorkOrder.Status status) {
}
