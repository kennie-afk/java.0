package com.smartseason.catalog.web.dto;

import com.smartseason.catalog.domain.Certification;
import java.time.Instant;
import java.util.UUID;

public record CertificationResponse(
        UUID id,
        String code,
        String name,
        String issuingBody,
        String description,
        Integer validityMonths,
        Instant createdAt,
        Instant updatedAt) {

    public static CertificationResponse from(Certification entity) {
        return new CertificationResponse(
                entity.getId(),
                entity.getCode(),
                entity.getName(),
                entity.getIssuingBody(),
                entity.getDescription(),
                entity.getValidityMonths(),
                entity.getCreatedAt(),
                entity.getUpdatedAt());
    }
}
