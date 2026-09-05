package com.smartseason.deviceregistry.web.dto;

import com.smartseason.deviceregistry.domain.DeviceCredential;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;

public record DeviceCredentialUpdateRequest(
        UUID deviceId,
        DeviceCredential.CredentialType credentialType,
        String publicKey,
        @Size(max = 255) String fingerprint,
        Instant issuedAt,
        Instant expiresAt,
        Instant revokedAt) {
}
