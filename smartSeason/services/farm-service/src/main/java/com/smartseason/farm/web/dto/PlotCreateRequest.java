package com.smartseason.farm.web.dto;

import com.smartseason.farm.domain.Plot;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.UUID;

public record PlotCreateRequest(
        @NotNull UUID farmId,
        @NotBlank @Size(max = 255) String name,
        @NotNull BigDecimal areaHa,
        String boundaryGeojson,
        BigDecimal centroidLat,
        BigDecimal centroidLng,
        @NotNull Boolean irrigated,
        @Size(max = 255) String currentCrop,
        @NotNull Plot.Status status) {
}
