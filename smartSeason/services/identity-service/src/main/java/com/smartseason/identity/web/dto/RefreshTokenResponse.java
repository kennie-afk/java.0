package com.smartseason.identity.web.dto;

import com.smartseason.identity.domain.RefreshToken;
import java.time.Instant;
import java.util.UUID;

public record RefreshTokenResponse(
        UUID id,
        UUID userId,
        String tokenHash,
        Instant expiresAt,
        Instant revokedAt,
        String userAgent,
        String ip,
        Instant createdAt,
        Instant updatedAt) {

    public static RefreshTokenResponse from(RefreshToken entity) {
        return new RefreshTokenResponse(
                entity.getId(),
                entity.getUserId(),
                entity.getTokenHash(),
                entity.getExpiresAt(),
                entity.getRevokedAt(),
                entity.getUserAgent(),
                entity.getIp(),
                entity.getCreatedAt(),
                entity.getUpdatedAt());
    }
}
