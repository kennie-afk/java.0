package com.smartseason.task.web.dto;

import com.smartseason.task.domain.WorkOrder;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record WorkOrderResponse(
        UUID id,
        UUID farmId,
        UUID plotId,
        UUID seasonId,
        String taskCode,
        String title,
        String description,
        LocalDate dueDate,
        WorkOrder.Priority priority,
        BigDecimal estimatedHours,
        UUID createdBy,
        WorkOrder.Status status,
        Instant createdAt,
        Instant updatedAt) {

    public static WorkOrderResponse from(WorkOrder entity) {
        return new WorkOrderResponse(
                entity.getId(),
                entity.getFarmId(),
                entity.getPlotId(),
                entity.getSeasonId(),
                entity.getTaskCode(),
                entity.getTitle(),
                entity.getDescription(),
                entity.getDueDate(),
                entity.getPriority(),
                entity.getEstimatedHours(),
                entity.getCreatedBy(),
                entity.getStatus(),
                entity.getCreatedAt(),
                entity.getUpdatedAt());
    }
}
