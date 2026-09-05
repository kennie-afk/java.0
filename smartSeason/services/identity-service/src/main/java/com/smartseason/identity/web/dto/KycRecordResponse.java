package com.smartseason.identity.web.dto;

import com.smartseason.identity.domain.KycRecord;
import java.time.Instant;
import java.util.UUID;

public record KycRecordResponse(
        UUID id,
        UUID subjectId,
        KycRecord.SubjectType subjectType,
        String idNumber,
        String documentUrl,
        KycRecord.Status status,
        UUID reviewedBy,
        String reviewNotes,
        Instant createdAt,
        Instant updatedAt) {

    public static KycRecordResponse from(KycRecord entity) {
        return new KycRecordResponse(
                entity.getId(),
                entity.getSubjectId(),
                entity.getSubjectType(),
                entity.getIdNumber(),
                entity.getDocumentUrl(),
                entity.getStatus(),
                entity.getReviewedBy(),
                entity.getReviewNotes(),
                entity.getCreatedAt(),
                entity.getUpdatedAt());
    }
}
