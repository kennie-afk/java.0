package com.smartseason.audit.chain;

import java.time.Instant;
import java.util.UUID;

public record AuditEntry(
        long sequence,
        String serviceName,
        UUID actorUserId,
        String actorRole,
        String action,
        String resourceType,
        String resourceId,
        String outcome,
        Instant occurredAt,
        String ipAddress,
        String details) {
}
