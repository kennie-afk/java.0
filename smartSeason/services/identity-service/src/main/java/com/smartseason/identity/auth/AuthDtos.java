package com.smartseason.identity.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public final class AuthDtos {

    private AuthDtos() {
    }

    public record RegisterRequest(
            @NotBlank @Size(max = 255) String organisationName,
            @NotBlank @Size(max = 255) String fullName,
            @NotBlank @Email @Size(max = 255) String email,
            @NotBlank @Size(min = 12, max = 200) String password,
            @Size(max = 32) String phone,
            @Size(max = 64) String orgType) {
    }

    public record LoginRequest(
            @NotBlank @Email String email,
            @NotBlank String password) {
    }

    public record TokenResponse(
            String accessToken,
            String refreshToken,
            String tokenType,
            long expiresIn,
            UUID userId,
            UUID organisationId,
            String roles) {
    }

    public record ProfileResponse(
            UUID id,
            String email,
            String fullName,
            String phone,
            UUID organisationId,
            String roles,
            String status,
            String locale) {
    }

    public record RefreshRequest(@NotBlank String refreshToken) {
    }
}
