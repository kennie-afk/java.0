package com.smartseason.audit.web.dto;

import com.smartseason.audit.domain.AuditAnchor;
import java.time.Instant;
import java.util.UUID;

public record AuditAnchorResponse(
        UUID id,
        Long anchorSequence,
        String chainHash,
        Long recordCount,
        Instant anchoredAt,
        String externalRef,
        Instant createdAt,
        Instant updatedAt) {

    public static AuditAnchorResponse from(AuditAnchor entity) {
        return new AuditAnchorResponse(
                entity.getId(),
                entity.getAnchorSequence(),
                entity.getChainHash(),
                entity.getRecordCount(),
                entity.getAnchoredAt(),
                entity.getExternalRef(),
                entity.getCreatedAt(),
                entity.getUpdatedAt());
    }
}
