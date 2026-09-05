package com.smartseason.media.web.dto;

import com.smartseason.media.domain.UploadTicket;
import java.time.Instant;
import java.util.UUID;

public record UploadTicketResponse(
        UUID id,
        String storageKey,
        String uploadUrl,
        String method,
        UUID requestedBy,
        String contentType,
        Long maxSizeBytes,
        Instant expiresAt,
        Instant consumedAt,
        UploadTicket.Status status,
        Instant createdAt,
        Instant updatedAt) {

    public static UploadTicketResponse from(UploadTicket entity) {
        return new UploadTicketResponse(
                entity.getId(),
                entity.getStorageKey(),
                entity.getUploadUrl(),
                entity.getMethod(),
                entity.getRequestedBy(),
                entity.getContentType(),
                entity.getMaxSizeBytes(),
                entity.getExpiresAt(),
                entity.getConsumedAt(),
                entity.getStatus(),
                entity.getCreatedAt(),
                entity.getUpdatedAt());
    }
}
