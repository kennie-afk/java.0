package com.smartseason.identity.web.dto;

import com.smartseason.identity.domain.RefreshToken;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;

public record RefreshTokenCreateRequest(
        @NotNull UUID userId,
        @NotBlank @Size(max = 255) String tokenHash,
        @NotNull Instant expiresAt,
        Instant revokedAt,
        @Size(max = 255) String userAgent,
        @Size(max = 255) String ip) {
}
