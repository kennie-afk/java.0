package com.smartseason.fraud.web.dto;

import com.smartseason.fraud.domain.FraudEvidence;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record FraudEvidenceCreateRequest(
        @NotNull UUID caseId,
        @NotBlank @Size(max = 255) String label,
        @NotBlank @Size(max = 255) String evidenceType,
        String payload,
        BigDecimal weight,
        @NotNull Instant collectedAt) {
}
