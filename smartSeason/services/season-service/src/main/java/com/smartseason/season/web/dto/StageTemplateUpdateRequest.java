package com.smartseason.season.web.dto;

import com.smartseason.season.domain.StageTemplate;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record StageTemplateUpdateRequest(
        @Size(max = 255) String cropCode,
        @Size(max = 255) String stageName,
        Integer sequence,
        Integer durationDays,
        String description,
        String keyActivities) {
}
