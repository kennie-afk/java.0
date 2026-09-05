package com.smartseason.agronomy.web.dto;

import com.smartseason.agronomy.domain.ScoutingReport;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record ScoutingReportResponse(
        UUID id,
        UUID plotId,
        UUID seasonId,
        UUID scoutedBy,
        Instant scoutedAt,
        String pestDiseaseCode,
        BigDecimal incidencePct,
        Integer severityScore,
        BigDecimal latitude,
        BigDecimal longitude,
        String photoUrl,
        String notes,
        ScoutingReport.Status status,
        Instant createdAt,
        Instant updatedAt) {

    public static ScoutingReportResponse from(ScoutingReport entity) {
        return new ScoutingReportResponse(
                entity.getId(),
                entity.getPlotId(),
                entity.getSeasonId(),
                entity.getScoutedBy(),
                entity.getScoutedAt(),
                entity.getPestDiseaseCode(),
                entity.getIncidencePct(),
                entity.getSeverityScore(),
                entity.getLatitude(),
                entity.getLongitude(),
                entity.getPhotoUrl(),
                entity.getNotes(),
                entity.getStatus(),
                entity.getCreatedAt(),
                entity.getUpdatedAt());
    }
}
