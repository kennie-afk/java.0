package com.smartseason.farm.web.dto;

import com.smartseason.farm.domain.SoilProfile;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record SoilProfileUpdateRequest(
        UUID plotId,
        LocalDate sampledAt,
        BigDecimal ph,
        BigDecimal nitrogenPpm,
        BigDecimal phosphorusPpm,
        BigDecimal potassiumPpm,
        BigDecimal organicCarbonPct,
        @Size(max = 255) String texture,
        @Size(max = 255) String labName,
        @Size(max = 255) String reportUrl) {
}
