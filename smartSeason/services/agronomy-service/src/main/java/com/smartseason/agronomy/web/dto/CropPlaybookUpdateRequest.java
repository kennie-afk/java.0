package com.smartseason.agronomy.web.dto;

import com.smartseason.agronomy.domain.CropPlaybook;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CropPlaybookUpdateRequest(
        @Size(max = 255) String cropCode,
        @Size(max = 255) String stageName,
        String guidance,
        String inputRecommendations,
        String riskFactors,
        Integer revision) {
}
