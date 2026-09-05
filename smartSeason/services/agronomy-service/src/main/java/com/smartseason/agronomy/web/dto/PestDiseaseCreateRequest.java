package com.smartseason.agronomy.web.dto;

import com.smartseason.agronomy.domain.PestDisease;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record PestDiseaseCreateRequest(
        @NotBlank @Size(max = 255) String code,
        @NotBlank @Size(max = 255) String commonName,
        @Size(max = 255) String scientificName,
        @NotNull PestDisease.Type type,
        @Size(max = 255) String affectedCrops,
        String symptoms,
        String management,
        @Size(max = 255) String imageUrl) {
}
