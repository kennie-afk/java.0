package com.smartseason.logistics.web.dto;

import com.smartseason.logistics.domain.ColdChainReading;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record ColdChainReadingUpdateRequest(
        UUID transportJobId,
        Instant recordedAt,
        BigDecimal temperatureC,
        BigDecimal humidityPct,
        @Size(max = 255) String deviceId,
        Boolean breach) {
}
