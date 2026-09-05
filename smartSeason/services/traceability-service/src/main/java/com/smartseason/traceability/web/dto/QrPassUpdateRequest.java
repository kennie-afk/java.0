package com.smartseason.traceability.web.dto;

import com.smartseason.traceability.domain.QrPass;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;

public record QrPassUpdateRequest(
        @Size(max = 255) String batchCode,
        @Size(max = 255) String passCode,
        @Size(max = 255) String qrUrl,
        Instant issuedAt,
        Instant expiresAt,
        Integer scanCount,
        Instant lastScannedAt,
        String publicSummary,
        QrPass.Status status) {
}
