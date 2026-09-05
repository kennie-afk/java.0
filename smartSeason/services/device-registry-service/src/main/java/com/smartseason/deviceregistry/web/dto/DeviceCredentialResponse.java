package com.smartseason.deviceregistry.web.dto;

import com.smartseason.deviceregistry.domain.DeviceCredential;
import java.time.Instant;
import java.util.UUID;

public record DeviceCredentialResponse(
        UUID id,
        UUID deviceId,
        DeviceCredential.CredentialType credentialType,
        String publicKey,
        String fingerprint,
        Instant issuedAt,
        Instant expiresAt,
        Instant revokedAt,
        Instant createdAt,
        Instant updatedAt) {

    public static DeviceCredentialResponse from(DeviceCredential entity) {
        return new DeviceCredentialResponse(
                entity.getId(),
                entity.getDeviceId(),
                entity.getCredentialType(),
                entity.getPublicKey(),
                entity.getFingerprint(),
                entity.getIssuedAt(),
                entity.getExpiresAt(),
                entity.getRevokedAt(),
                entity.getCreatedAt(),
                entity.getUpdatedAt());
    }
}
