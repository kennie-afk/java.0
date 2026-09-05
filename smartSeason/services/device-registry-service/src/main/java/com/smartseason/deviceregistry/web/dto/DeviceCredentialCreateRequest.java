package com.smartseason.deviceregistry.web.dto;

import com.smartseason.deviceregistry.domain.DeviceCredential;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;

public record DeviceCredentialCreateRequest(
        @NotNull UUID deviceId,
        @NotNull DeviceCredential.CredentialType credentialType,
        String publicKey,
        @Size(max = 255) String fingerprint,
        @NotNull Instant issuedAt,
        Instant expiresAt,
        Instant revokedAt) {
}
