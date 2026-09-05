package com.smartseason.identity.web.dto;

import com.smartseason.identity.domain.OtpChallenge;
import java.time.Instant;
import java.util.UUID;

public record OtpChallengeResponse(
        UUID id,
        UUID userId,
        String destination,
        OtpChallenge.Channel channel,
        String codeHash,
        String purpose,
        Instant expiresAt,
        Instant consumedAt,
        Integer attempts,
        Instant createdAt,
        Instant updatedAt) {

    public static OtpChallengeResponse from(OtpChallenge entity) {
        return new OtpChallengeResponse(
                entity.getId(),
                entity.getUserId(),
                entity.getDestination(),
                entity.getChannel(),
                entity.getCodeHash(),
                entity.getPurpose(),
                entity.getExpiresAt(),
                entity.getConsumedAt(),
                entity.getAttempts(),
                entity.getCreatedAt(),
                entity.getUpdatedAt());
    }
}
