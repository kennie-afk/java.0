package com.smartseason.automation.engine;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record RuleDefinition(
        UUID id,
        String name,
        UUID plotId,
        String triggerMetric,
        Operator operator,
        BigDecimal threshold,
        String actionType,
        UUID actionTargetDeviceId,
        Integer durationSeconds,
        int cooldownSeconds,
        boolean enabled,
        Instant lastTriggeredAt) {

    public enum Operator {
        LT, LTE, GT, GTE, EQ;

        public boolean test(BigDecimal observed, BigDecimal threshold) {
            int comparison = observed.compareTo(threshold);
            return switch (this) {
                case LT -> comparison < 0;
                case LTE -> comparison <= 0;
                case GT -> comparison > 0;
                case GTE -> comparison >= 0;
                case EQ -> comparison == 0;
            };
        }
    }
}
