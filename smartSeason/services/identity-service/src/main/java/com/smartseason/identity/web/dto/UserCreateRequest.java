package com.smartseason.identity.web.dto;

import com.smartseason.identity.domain.User;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;

public record UserCreateRequest(
        @NotBlank @Size(max = 255) String email,
        @Size(max = 255) String phone,
        @NotBlank @Size(max = 255) String fullName,
        @NotBlank @Size(max = 255) String passwordHash,
        UUID organisationId,
        @NotBlank @Size(max = 255) String roles,
        @NotNull User.Status status,
        @NotNull Boolean mfaEnabled,
        Instant lastLoginAt,
        @NotNull Integer failedAttempts,
        @Size(max = 255) String locale) {
}
