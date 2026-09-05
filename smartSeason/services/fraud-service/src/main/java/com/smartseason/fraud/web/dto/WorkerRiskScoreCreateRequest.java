package com.smartseason.fraud.web.dto;

import com.smartseason.fraud.domain.WorkerRiskScore;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;

public record WorkerRiskScoreCreateRequest(
        @NotNull UUID workerId,
        UUID farmId,
        @NotNull Integer score,
        @NotNull WorkerRiskScore.Band band,
        @NotNull Integer openCases,
        Instant lastSignalAt,
        Instant decayAppliedAt,
        String components) {
}
