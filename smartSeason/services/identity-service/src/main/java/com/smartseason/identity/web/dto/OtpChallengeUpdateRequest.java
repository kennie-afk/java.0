package com.smartseason.identity.web.dto;

import com.smartseason.identity.domain.OtpChallenge;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;

public record OtpChallengeUpdateRequest(
        UUID userId,
        @Size(max = 255) String destination,
        OtpChallenge.Channel channel,
        @Size(max = 255) String codeHash,
        @Size(max = 255) String purpose,
        Instant expiresAt,
        Instant consumedAt,
        Integer attempts) {
}
