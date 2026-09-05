package com.smartseason.fraud.web.dto;

import com.smartseason.fraud.domain.FraudSignal;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record FraudSignalResponse(
        UUID id,
        FraudSignal.SubjectType subjectType,
        UUID subjectId,
        String ruleCode,
        String typology,
        BigDecimal score,
        Instant detectedAt,
        String sourceEvent,
        String details,
        UUID caseId,
        Instant createdAt,
        Instant updatedAt) {

    public static FraudSignalResponse from(FraudSignal entity) {
        return new FraudSignalResponse(
                entity.getId(),
                entity.getSubjectType(),
                entity.getSubjectId(),
                entity.getRuleCode(),
                entity.getTypology(),
                entity.getScore(),
                entity.getDetectedAt(),
                entity.getSourceEvent(),
                entity.getDetails(),
                entity.getCaseId(),
                entity.getCreatedAt(),
                entity.getUpdatedAt());
    }
}
