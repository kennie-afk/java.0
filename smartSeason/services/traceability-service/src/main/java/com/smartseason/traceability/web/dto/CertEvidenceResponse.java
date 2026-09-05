package com.smartseason.traceability.web.dto;

import com.smartseason.traceability.domain.CertEvidence;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record CertEvidenceResponse(
        UUID id,
        String batchCode,
        UUID farmId,
        String certificationCode,
        String certificateNo,
        String issuedBy,
        LocalDate issuedOn,
        LocalDate expiresOn,
        String documentUrl,
        Boolean verified,
        Instant verifiedAt,
        Instant createdAt,
        Instant updatedAt) {

    public static CertEvidenceResponse from(CertEvidence entity) {
        return new CertEvidenceResponse(
                entity.getId(),
                entity.getBatchCode(),
                entity.getFarmId(),
                entity.getCertificationCode(),
                entity.getCertificateNo(),
                entity.getIssuedBy(),
                entity.getIssuedOn(),
                entity.getExpiresOn(),
                entity.getDocumentUrl(),
                entity.getVerified(),
                entity.getVerifiedAt(),
                entity.getCreatedAt(),
                entity.getUpdatedAt());
    }
}
