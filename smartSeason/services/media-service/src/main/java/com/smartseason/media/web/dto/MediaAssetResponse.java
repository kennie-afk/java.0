package com.smartseason.media.web.dto;

import com.smartseason.media.domain.MediaAsset;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record MediaAssetResponse(
        UUID id,
        String storageKey,
        String originalFilename,
        String contentType,
        Long sizeBytes,
        String checksum,
        UUID ownerUserId,
        String context,
        String contextRef,
        Integer width,
        Integer height,
        Integer durationSeconds,
        String perceptualHash,
        Instant exifTimestamp,
        BigDecimal exifLatitude,
        BigDecimal exifLongitude,
        String publicUrl,
        Boolean virusScanned,
        Boolean virusClean,
        MediaAsset.Status status,
        Instant createdAt,
        Instant updatedAt) {

    public static MediaAssetResponse from(MediaAsset entity) {
        return new MediaAssetResponse(
                entity.getId(),
                entity.getStorageKey(),
                entity.getOriginalFilename(),
                entity.getContentType(),
                entity.getSizeBytes(),
                entity.getChecksum(),
                entity.getOwnerUserId(),
                entity.getContext(),
                entity.getContextRef(),
                entity.getWidth(),
                entity.getHeight(),
                entity.getDurationSeconds(),
                entity.getPerceptualHash(),
                entity.getExifTimestamp(),
                entity.getExifLatitude(),
                entity.getExifLongitude(),
                entity.getPublicUrl(),
                entity.getVirusScanned(),
                entity.getVirusClean(),
                entity.getStatus(),
                entity.getCreatedAt(),
                entity.getUpdatedAt());
    }
}
