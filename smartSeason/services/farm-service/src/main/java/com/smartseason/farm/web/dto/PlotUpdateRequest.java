package com.smartseason.farm.web.dto;

import com.smartseason.farm.domain.Plot;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.UUID;

public record PlotUpdateRequest(
        UUID farmId,
        @Size(max = 255) String name,
        BigDecimal areaHa,
        String boundaryGeojson,
        BigDecimal centroidLat,
        BigDecimal centroidLng,
        Boolean irrigated,
        @Size(max = 255) String currentCrop,
        Plot.Status status) {
}
