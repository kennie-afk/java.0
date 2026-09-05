package com.smartseason.agronomy.web.dto;

import com.smartseason.agronomy.domain.Advisory;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;

public record AdvisoryCreateRequest(
        UUID seasonId,
        UUID plotId,
        @Size(max = 255) String cropCode,
        @NotBlank @Size(max = 255) String title,
        @NotBlank String body,
        @NotNull Advisory.Severity severity,
        @NotNull Advisory.Source source,
        @NotNull Instant issuedAt,
        Instant acknowledgedAt,
        UUID acknowledgedBy) {
}
