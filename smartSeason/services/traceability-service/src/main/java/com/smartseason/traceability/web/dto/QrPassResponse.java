package com.smartseason.traceability.web.dto;

import com.smartseason.traceability.domain.QrPass;
import java.time.Instant;
import java.util.UUID;

public record QrPassResponse(
        UUID id,
        String batchCode,
        String passCode,
        String qrUrl,
        Instant issuedAt,
        Instant expiresAt,
        Integer scanCount,
        Instant lastScannedAt,
        String publicSummary,
        QrPass.Status status,
        Instant createdAt,
        Instant updatedAt) {

    public static QrPassResponse from(QrPass entity) {
        return new QrPassResponse(
                entity.getId(),
                entity.getBatchCode(),
                entity.getPassCode(),
                entity.getQrUrl(),
                entity.getIssuedAt(),
                entity.getExpiresAt(),
                entity.getScanCount(),
                entity.getLastScannedAt(),
                entity.getPublicSummary(),
                entity.getStatus(),
                entity.getCreatedAt(),
                entity.getUpdatedAt());
    }
}
