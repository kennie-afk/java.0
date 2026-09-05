package com.smartseason.traceability.web.dto;

import com.smartseason.traceability.domain.TraceLink;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record TraceLinkUpdateRequest(
        @Size(max = 255) String batchCode,
        Integer sequence,
        TraceLink.NodeType nodeType,
        @Size(max = 255) String nodeRef,
        Instant occurredAt,
        UUID actorOrgId,
        @Size(max = 255) String location,
        BigDecimal latitude,
        BigDecimal longitude,
        String attributes,
        @Size(max = 255) String evidenceUrl) {
}
