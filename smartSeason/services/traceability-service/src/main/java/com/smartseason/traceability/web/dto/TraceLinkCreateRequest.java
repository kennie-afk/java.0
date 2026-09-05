package com.smartseason.traceability.web.dto;

import com.smartseason.traceability.domain.TraceLink;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record TraceLinkCreateRequest(
        @NotBlank @Size(max = 255) String batchCode,
        @NotNull Integer sequence,
        @NotNull TraceLink.NodeType nodeType,
        @NotBlank @Size(max = 255) String nodeRef,
        @NotNull Instant occurredAt,
        UUID actorOrgId,
        @Size(max = 255) String location,
        BigDecimal latitude,
        BigDecimal longitude,
        String attributes,
        @Size(max = 255) String evidenceUrl) {
}
