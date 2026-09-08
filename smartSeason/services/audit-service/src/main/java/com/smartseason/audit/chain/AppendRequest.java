package com.smartseason.audit.chain;

import jakarta.validation.constraints.NotBlank;
import java.time.Instant;
import java.util.UUID;

/**
 * What a caller may state about an event. Deliberately excludes sequence,
 * previousHash and recordHash - those belong to the service.
 */
public record AppendRequest(
        @NotBlank String serviceName,
        UUID actorUserId,
        String actorRole,
        @NotBlank String action,
        String resourceType,
        String resourceId,
        String outcome,
        Instant occurredAt,
        String ipAddress,
        String userAgent,
        String details) {
}
