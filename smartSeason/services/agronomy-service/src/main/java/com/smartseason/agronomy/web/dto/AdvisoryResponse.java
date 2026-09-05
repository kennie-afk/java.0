package com.smartseason.agronomy.web.dto;

import com.smartseason.agronomy.domain.Advisory;
import java.time.Instant;
import java.util.UUID;

public record AdvisoryResponse(
        UUID id,
        UUID seasonId,
        UUID plotId,
        String cropCode,
        String title,
        String body,
        Advisory.Severity severity,
        Advisory.Source source,
        Instant issuedAt,
        Instant acknowledgedAt,
        UUID acknowledgedBy,
        Instant createdAt,
        Instant updatedAt) {

    public static AdvisoryResponse from(Advisory entity) {
        return new AdvisoryResponse(
                entity.getId(),
                entity.getSeasonId(),
                entity.getPlotId(),
                entity.getCropCode(),
                entity.getTitle(),
                entity.getBody(),
                entity.getSeverity(),
                entity.getSource(),
                entity.getIssuedAt(),
                entity.getAcknowledgedAt(),
                entity.getAcknowledgedBy(),
                entity.getCreatedAt(),
                entity.getUpdatedAt());
    }
}
