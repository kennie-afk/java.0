package com.smartseason.agronomy.web.dto;

import com.smartseason.agronomy.domain.Advisory;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;

public record AdvisoryUpdateRequest(
        UUID seasonId,
        UUID plotId,
        @Size(max = 255) String cropCode,
        @Size(max = 255) String title,
        String body,
        Advisory.Severity severity,
        Advisory.Source source,
        Instant issuedAt,
        Instant acknowledgedAt,
        UUID acknowledgedBy) {
}
