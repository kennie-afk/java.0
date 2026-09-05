package com.smartseason.task.web.dto;

import com.smartseason.task.domain.TaskEvidence;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record TaskEvidenceResponse(
        UUID id,
        UUID assignmentId,
        UUID workOrderId,
        TaskEvidence.EvidenceType evidenceType,
        String mediaUrl,
        String perceptualHash,
        BigDecimal latitude,
        BigDecimal longitude,
        Instant capturedAt,
        Instant exifTimestamp,
        Boolean mockLocation,
        String notes,
        TaskEvidence.Verdict verdict,
        Instant createdAt,
        Instant updatedAt) {

    public static TaskEvidenceResponse from(TaskEvidence entity) {
        return new TaskEvidenceResponse(
                entity.getId(),
                entity.getAssignmentId(),
                entity.getWorkOrderId(),
                entity.getEvidenceType(),
                entity.getMediaUrl(),
                entity.getPerceptualHash(),
                entity.getLatitude(),
                entity.getLongitude(),
                entity.getCapturedAt(),
                entity.getExifTimestamp(),
                entity.getMockLocation(),
                entity.getNotes(),
                entity.getVerdict(),
                entity.getCreatedAt(),
                entity.getUpdatedAt());
    }
}
