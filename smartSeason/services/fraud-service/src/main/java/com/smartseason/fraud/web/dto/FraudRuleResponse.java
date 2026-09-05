package com.smartseason.fraud.web.dto;

import com.smartseason.fraud.domain.FraudRule;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record FraudRuleResponse(
        UUID id,
        String code,
        FraudRule.Typology typology,
        String name,
        String description,
        String expression,
        BigDecimal threshold,
        FraudRule.Severity severity,
        Integer weight,
        Boolean enabled,
        Boolean autoHoldPayout,
        Instant createdAt,
        Instant updatedAt) {

    public static FraudRuleResponse from(FraudRule entity) {
        return new FraudRuleResponse(
                entity.getId(),
                entity.getCode(),
                entity.getTypology(),
                entity.getName(),
                entity.getDescription(),
                entity.getExpression(),
                entity.getThreshold(),
                entity.getSeverity(),
                entity.getWeight(),
                entity.getEnabled(),
                entity.getAutoHoldPayout(),
                entity.getCreatedAt(),
                entity.getUpdatedAt());
    }
}
