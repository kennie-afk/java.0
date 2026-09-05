package com.smartseason.fraud.web.dto;

import com.smartseason.fraud.domain.FraudSignal;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record FraudSignalUpdateRequest(
        FraudSignal.SubjectType subjectType,
        UUID subjectId,
        @Size(max = 255) String ruleCode,
        @Size(max = 255) String typology,
        BigDecimal score,
        Instant detectedAt,
        @Size(max = 255) String sourceEvent,
        String details,
        UUID caseId) {
}
