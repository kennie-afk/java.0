package com.smartseason.season.web.dto;

import com.smartseason.season.domain.Season;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record SeasonCreateRequest(
        @NotNull UUID plotId,
        UUID farmId,
        @NotBlank @Size(max = 255) String cropCode,
        @Size(max = 255) String variety,
        @NotNull LocalDate startDate,
        LocalDate expectedHarvestDate,
        LocalDate actualHarvestDate,
        BigDecimal expectedYieldKg,
        BigDecimal actualYieldKg,
        @Size(max = 255) String currentStage,
        @NotNull Season.Status status) {
}
