package com.smartseason.automation.web.dto;

import com.smartseason.automation.domain.AutomationRule;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record AutomationRuleResponse(
        UUID id,
        String name,
        UUID plotId,
        String triggerMetric,
        AutomationRule.Operator operator,
        BigDecimal threshold,
        AutomationRule.ActionType actionType,
        UUID actionTargetDeviceId,
        Integer durationSeconds,
        Integer cooldownSeconds,
        Boolean enabled,
        Instant lastTriggeredAt,
        Instant createdAt,
        Instant updatedAt) {

    public static AutomationRuleResponse from(AutomationRule entity) {
        return new AutomationRuleResponse(
                entity.getId(),
                entity.getName(),
                entity.getPlotId(),
                entity.getTriggerMetric(),
                entity.getOperator(),
                entity.getThreshold(),
                entity.getActionType(),
                entity.getActionTargetDeviceId(),
                entity.getDurationSeconds(),
                entity.getCooldownSeconds(),
                entity.getEnabled(),
                entity.getLastTriggeredAt(),
                entity.getCreatedAt(),
                entity.getUpdatedAt());
    }
}
