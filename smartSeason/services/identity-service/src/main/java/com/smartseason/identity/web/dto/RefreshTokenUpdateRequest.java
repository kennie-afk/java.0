package com.smartseason.identity.web.dto;

import com.smartseason.identity.domain.RefreshToken;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;

public record RefreshTokenUpdateRequest(
        UUID userId,
        @Size(max = 255) String tokenHash,
        Instant expiresAt,
        Instant revokedAt,
        @Size(max = 255) String userAgent,
        @Size(max = 255) String ip) {
}
