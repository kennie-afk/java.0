package com.smartseason.fraud.web.dto;

import com.smartseason.fraud.domain.FraudEvidence;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record FraudEvidenceResponse(
        UUID id,
        UUID caseId,
        String label,
        String evidenceType,
        String payload,
        BigDecimal weight,
        Instant collectedAt,
        Instant createdAt,
        Instant updatedAt) {

    public static FraudEvidenceResponse from(FraudEvidence entity) {
        return new FraudEvidenceResponse(
                entity.getId(),
                entity.getCaseId(),
                entity.getLabel(),
                entity.getEvidenceType(),
                entity.getPayload(),
                entity.getWeight(),
                entity.getCollectedAt(),
                entity.getCreatedAt(),
                entity.getUpdatedAt());
    }
}
