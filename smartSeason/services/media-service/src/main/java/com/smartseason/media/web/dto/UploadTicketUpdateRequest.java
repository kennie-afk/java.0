package com.smartseason.media.web.dto;

import com.smartseason.media.domain.UploadTicket;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;

public record UploadTicketUpdateRequest(
        @Size(max = 255) String storageKey,
        String uploadUrl,
        @Size(max = 255) String method,
        UUID requestedBy,
        @Size(max = 255) String contentType,
        Long maxSizeBytes,
        Instant expiresAt,
        Instant consumedAt,
        UploadTicket.Status status) {
}
