package com.smartseason.task.web.dto;

import com.smartseason.task.domain.TaskEvidence;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record TaskEvidenceUpdateRequest(
        UUID assignmentId,
        UUID workOrderId,
        TaskEvidence.EvidenceType evidenceType,
        @Size(max = 255) String mediaUrl,
        @Size(max = 255) String perceptualHash,
        BigDecimal latitude,
        BigDecimal longitude,
        Instant capturedAt,
        Instant exifTimestamp,
        Boolean mockLocation,
        String notes,
        TaskEvidence.Verdict verdict) {
}
