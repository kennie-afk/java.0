package com.smartseason.media.web.dto;

import com.smartseason.media.domain.UploadTicket;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;

public record UploadTicketCreateRequest(
        @NotBlank @Size(max = 255) String storageKey,
        @NotBlank String uploadUrl,
        @NotBlank @Size(max = 255) String method,
        @NotNull UUID requestedBy,
        @Size(max = 255) String contentType,
        Long maxSizeBytes,
        @NotNull Instant expiresAt,
        Instant consumedAt,
        @NotNull UploadTicket.Status status) {
}
