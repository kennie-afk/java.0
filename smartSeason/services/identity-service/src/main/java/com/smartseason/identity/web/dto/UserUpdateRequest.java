package com.smartseason.identity.web.dto;

import com.smartseason.identity.domain.User;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;

public record UserUpdateRequest(
        @Size(max = 255) String email,
        @Size(max = 255) String phone,
        @Size(max = 255) String fullName,
        @Size(max = 255) String passwordHash,
        UUID organisationId,
        @Size(max = 255) String roles,
        User.Status status,
        Boolean mfaEnabled,
        Instant lastLoginAt,
        Integer failedAttempts,
        @Size(max = 255) String locale) {
}
