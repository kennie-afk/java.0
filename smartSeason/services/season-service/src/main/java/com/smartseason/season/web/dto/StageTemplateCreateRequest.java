package com.smartseason.season.web.dto;

import com.smartseason.season.domain.StageTemplate;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record StageTemplateCreateRequest(
        @NotBlank @Size(max = 255) String cropCode,
        @NotBlank @Size(max = 255) String stageName,
        @NotNull Integer sequence,
        @NotNull Integer durationDays,
        String description,
        String keyActivities) {
}
