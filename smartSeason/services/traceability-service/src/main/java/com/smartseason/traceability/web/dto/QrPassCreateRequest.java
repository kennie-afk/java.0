package com.smartseason.traceability.web.dto;

import com.smartseason.traceability.domain.QrPass;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;

public record QrPassCreateRequest(
        @NotBlank @Size(max = 255) String batchCode,
        @NotBlank @Size(max = 255) String passCode,
        @Size(max = 255) String qrUrl,
        @NotNull Instant issuedAt,
        Instant expiresAt,
        @NotNull Integer scanCount,
        Instant lastScannedAt,
        String publicSummary,
        @NotNull QrPass.Status status) {
}
