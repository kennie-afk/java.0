package com.smartseason.fraud.web.dto;

import com.smartseason.fraud.domain.FraudCase;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record FraudCaseResponse(
        UUID id,
        String caseNumber,
        FraudCase.SubjectType subjectType,
        UUID subjectId,
        UUID farmId,
        String typology,
        FraudCase.Severity severity,
        BigDecimal confidence,
        Instant openedAt,
        FraudCase.Status status,
        UUID assignedTo,
        Instant resolvedAt,
        String resolution,
        Boolean payoutHeld,
        Instant appealedAt,
        String appealOutcome,
        Instant createdAt,
        Instant updatedAt) {

    public static FraudCaseResponse from(FraudCase entity) {
        return new FraudCaseResponse(
                entity.getId(),
                entity.getCaseNumber(),
                entity.getSubjectType(),
                entity.getSubjectId(),
                entity.getFarmId(),
                entity.getTypology(),
                entity.getSeverity(),
                entity.getConfidence(),
                entity.getOpenedAt(),
                entity.getStatus(),
                entity.getAssignedTo(),
                entity.getResolvedAt(),
                entity.getResolution(),
                entity.getPayoutHeld(),
                entity.getAppealedAt(),
                entity.getAppealOutcome(),
                entity.getCreatedAt(),
                entity.getUpdatedAt());
    }
}
