package com.smartseason.fraud.web.dto;

import com.smartseason.fraud.domain.FraudCase;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record FraudCaseUpdateRequest(
        @Size(max = 255) String caseNumber,
        FraudCase.SubjectType subjectType,
        UUID subjectId,
        UUID farmId,
        @Size(max = 255) String typology,
        FraudCase.Severity severity,
        BigDecimal confidence,
        Instant openedAt,
        FraudCase.Status status,
        UUID assignedTo,
        Instant resolvedAt,
        String resolution,
        Boolean payoutHeld,
        Instant appealedAt,
        @Size(max = 255) String appealOutcome) {
}
