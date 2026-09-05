package com.smartseason.media.web.dto;

import com.smartseason.media.domain.MediaVariant;
import java.time.Instant;
import java.util.UUID;

public record MediaVariantResponse(
        UUID id,
        UUID assetId,
        String variantName,
        String storageKey,
        Integer width,
        Integer height,
        Long sizeBytes,
        String contentType,
        String publicUrl,
        Instant createdAt,
        Instant updatedAt) {

    public static MediaVariantResponse from(MediaVariant entity) {
        return new MediaVariantResponse(
                entity.getId(),
                entity.getAssetId(),
                entity.getVariantName(),
                entity.getStorageKey(),
                entity.getWidth(),
                entity.getHeight(),
                entity.getSizeBytes(),
                entity.getContentType(),
                entity.getPublicUrl(),
                entity.getCreatedAt(),
                entity.getUpdatedAt());
    }
}
