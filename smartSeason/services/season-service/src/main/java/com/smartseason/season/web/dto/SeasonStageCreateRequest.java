package com.smartseason.season.web.dto;

import com.smartseason.season.domain.SeasonStage;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.UUID;

public record SeasonStageCreateRequest(
        @NotNull UUID seasonId,
        @NotBlank @Size(max = 255) String stageName,
        @NotNull Integer sequence,
        LocalDate plannedStart,
        LocalDate plannedEnd,
        LocalDate actualStart,
        LocalDate actualEnd,
        @NotNull SeasonStage.Status status,
        String notes) {
}
