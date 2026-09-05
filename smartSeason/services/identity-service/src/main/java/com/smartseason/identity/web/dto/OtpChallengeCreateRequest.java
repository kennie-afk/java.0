package com.smartseason.identity.web.dto;

import com.smartseason.identity.domain.OtpChallenge;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;

public record OtpChallengeCreateRequest(
        UUID userId,
        @NotBlank @Size(max = 255) String destination,
        @NotNull OtpChallenge.Channel channel,
        @NotBlank @Size(max = 255) String codeHash,
        @NotBlank @Size(max = 255) String purpose,
        @NotNull Instant expiresAt,
        Instant consumedAt,
        @NotNull Integer attempts) {
}
