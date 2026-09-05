package com.smartseason.identity.web.dto;

import com.smartseason.identity.domain.KycRecord;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record KycRecordUpdateRequest(
        UUID subjectId,
        KycRecord.SubjectType subjectType,
        @Size(max = 255) String idNumber,
        @Size(max = 255) String documentUrl,
        KycRecord.Status status,
        UUID reviewedBy,
        String reviewNotes) {
}
