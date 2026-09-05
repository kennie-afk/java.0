package com.smartseason.agronomy.web.dto;

import com.smartseason.agronomy.domain.CropPlaybook;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CropPlaybookCreateRequest(
        @NotBlank @Size(max = 255) String cropCode,
        @NotBlank @Size(max = 255) String stageName,
        @NotBlank String guidance,
        String inputRecommendations,
        String riskFactors,
        @NotNull Integer revision) {
}
