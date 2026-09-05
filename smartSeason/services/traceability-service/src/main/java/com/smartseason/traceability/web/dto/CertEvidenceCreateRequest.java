package com.smartseason.traceability.web.dto;

import com.smartseason.traceability.domain.CertEvidence;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record CertEvidenceCreateRequest(
        @Size(max = 255) String batchCode,
        UUID farmId,
        @NotBlank @Size(max = 255) String certificationCode,
        @Size(max = 255) String certificateNo,
        @Size(max = 255) String issuedBy,
        LocalDate issuedOn,
        LocalDate expiresOn,
        @Size(max = 255) String documentUrl,
        @NotNull Boolean verified,
        Instant verifiedAt) {
}
