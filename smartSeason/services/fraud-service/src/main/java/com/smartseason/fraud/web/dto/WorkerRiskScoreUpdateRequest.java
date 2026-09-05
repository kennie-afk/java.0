package com.smartseason.fraud.web.dto;

import com.smartseason.fraud.domain.WorkerRiskScore;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;

public record WorkerRiskScoreUpdateRequest(
        UUID workerId,
        UUID farmId,
        Integer score,
        WorkerRiskScore.Band band,
        Integer openCases,
        Instant lastSignalAt,
        Instant decayAppliedAt,
        String components) {
}
