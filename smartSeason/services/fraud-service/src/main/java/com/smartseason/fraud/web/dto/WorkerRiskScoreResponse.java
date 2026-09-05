package com.smartseason.fraud.web.dto;

import com.smartseason.fraud.domain.WorkerRiskScore;
import java.time.Instant;
import java.util.UUID;

public record WorkerRiskScoreResponse(
        UUID id,
        UUID workerId,
        UUID farmId,
        Integer score,
        WorkerRiskScore.Band band,
        Integer openCases,
        Instant lastSignalAt,
        Instant decayAppliedAt,
        String components,
        Instant createdAt,
        Instant updatedAt) {

    public static WorkerRiskScoreResponse from(WorkerRiskScore entity) {
        return new WorkerRiskScoreResponse(
                entity.getId(),
                entity.getWorkerId(),
                entity.getFarmId(),
                entity.getScore(),
                entity.getBand(),
                entity.getOpenCases(),
                entity.getLastSignalAt(),
                entity.getDecayAppliedAt(),
                entity.getComponents(),
                entity.getCreatedAt(),
                entity.getUpdatedAt());
    }
}
