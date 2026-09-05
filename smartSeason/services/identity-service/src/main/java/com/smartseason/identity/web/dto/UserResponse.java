package com.smartseason.identity.web.dto;

import com.smartseason.identity.domain.User;
import java.time.Instant;
import java.util.UUID;

public record UserResponse(
        UUID id,
        String email,
        String phone,
        String fullName,
        String passwordHash,
        UUID organisationId,
        String roles,
        User.Status status,
        Boolean mfaEnabled,
        Instant lastLoginAt,
        Integer failedAttempts,
        String locale,
        Instant createdAt,
        Instant updatedAt) {

    public static UserResponse from(User entity) {
        return new UserResponse(
                entity.getId(),
                entity.getEmail(),
                entity.getPhone(),
                entity.getFullName(),
                entity.getPasswordHash(),
                entity.getOrganisationId(),
                entity.getRoles(),
                entity.getStatus(),
                entity.getMfaEnabled(),
                entity.getLastLoginAt(),
                entity.getFailedAttempts(),
                entity.getLocale(),
                entity.getCreatedAt(),
                entity.getUpdatedAt());
    }
}
