package com.smartseason.fraud.web.dto;

import com.smartseason.fraud.domain.FraudEvidence;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record FraudEvidenceUpdateRequest(
        UUID caseId,
        @Size(max = 255) String label,
        @Size(max = 255) String evidenceType,
        String payload,
        BigDecimal weight,
        Instant collectedAt) {
}
