package com.smartseason.audit.web.dto;

import com.smartseason.audit.domain.AuditRecord;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;

public record AuditRecordCreateRequest(
        @NotNull Long sequence,
        @NotBlank @Size(max = 255) String serviceName,
        UUID actorUserId,
        @Size(max = 255) String actorRole,
        @NotBlank @Size(max = 255) String action,
        @Size(max = 255) String resourceType,
        @Size(max = 255) String resourceId,
        @NotNull AuditRecord.Outcome outcome,
        @NotNull Instant occurredAt,
        @Size(max = 255) String ipAddress,
        @Size(max = 255) String userAgent,
        String details,
        @Size(max = 255) String previousHash,
        @NotBlank @Size(max = 255) String recordHash) {
}
