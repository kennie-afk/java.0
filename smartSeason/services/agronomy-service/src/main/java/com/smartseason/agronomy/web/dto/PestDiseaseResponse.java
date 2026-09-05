package com.smartseason.agronomy.web.dto;

import com.smartseason.agronomy.domain.PestDisease;
import java.time.Instant;
import java.util.UUID;

public record PestDiseaseResponse(
        UUID id,
        String code,
        String commonName,
        String scientificName,
        PestDisease.Type type,
        String affectedCrops,
        String symptoms,
        String management,
        String imageUrl,
        Instant createdAt,
        Instant updatedAt) {

    public static PestDiseaseResponse from(PestDisease entity) {
        return new PestDiseaseResponse(
                entity.getId(),
                entity.getCode(),
                entity.getCommonName(),
                entity.getScientificName(),
                entity.getType(),
                entity.getAffectedCrops(),
                entity.getSymptoms(),
                entity.getManagement(),
                entity.getImageUrl(),
                entity.getCreatedAt(),
                entity.getUpdatedAt());
    }
}
