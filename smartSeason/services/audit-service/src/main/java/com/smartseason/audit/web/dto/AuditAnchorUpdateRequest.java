package com.smartseason.audit.web.dto;

import com.smartseason.audit.domain.AuditAnchor;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;

public record AuditAnchorUpdateRequest(
        Long anchorSequence,
        @Size(max = 255) String chainHash,
        Long recordCount,
        Instant anchoredAt,
        @Size(max = 255) String externalRef) {
}
