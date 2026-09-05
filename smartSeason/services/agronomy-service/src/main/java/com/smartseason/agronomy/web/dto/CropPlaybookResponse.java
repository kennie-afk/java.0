package com.smartseason.agronomy.web.dto;

import com.smartseason.agronomy.domain.CropPlaybook;
import java.time.Instant;
import java.util.UUID;

public record CropPlaybookResponse(
        UUID id,
        String cropCode,
        String stageName,
        String guidance,
        String inputRecommendations,
        String riskFactors,
        Integer revision,
        Instant createdAt,
        Instant updatedAt) {

    public static CropPlaybookResponse from(CropPlaybook entity) {
        return new CropPlaybookResponse(
                entity.getId(),
                entity.getCropCode(),
                entity.getStageName(),
                entity.getGuidance(),
                entity.getInputRecommendations(),
                entity.getRiskFactors(),
                entity.getRevision(),
                entity.getCreatedAt(),
                entity.getUpdatedAt());
    }
}
