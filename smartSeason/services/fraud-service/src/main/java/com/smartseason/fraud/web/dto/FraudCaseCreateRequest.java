package com.smartseason.fraud.web.dto;

import com.smartseason.fraud.domain.FraudCase;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record FraudCaseCreateRequest(
        @NotBlank @Size(max = 255) String caseNumber,
        @NotNull FraudCase.SubjectType subjectType,
        @NotNull UUID subjectId,
        UUID farmId,
        @NotBlank @Size(max = 255) String typology,
        @NotNull FraudCase.Severity severity,
        @NotNull BigDecimal confidence,
        @NotNull Instant openedAt,
        @NotNull FraudCase.Status status,
        UUID assignedTo,
        Instant resolvedAt,
        String resolution,
        @NotNull Boolean payoutHeld,
        Instant appealedAt,
        @Size(max = 255) String appealOutcome) {
}
