package com.smartseason.automation.web.dto;

import com.smartseason.automation.domain.AutomationRule;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record AutomationRuleUpdateRequest(
        @Size(max = 255) String name,
        UUID plotId,
        @Size(max = 255) String triggerMetric,
        AutomationRule.Operator operator,
        BigDecimal threshold,
        AutomationRule.ActionType actionType,
        UUID actionTargetDeviceId,
        Integer durationSeconds,
        Integer cooldownSeconds,
        Boolean enabled,
        Instant lastTriggeredAt) {
}
