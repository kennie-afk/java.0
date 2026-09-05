package com.smartseason.media.web.dto;

import com.smartseason.media.domain.MediaAsset;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record MediaAssetCreateRequest(
        @NotBlank @Size(max = 255) String storageKey,
        @Size(max = 255) String originalFilename,
        @NotBlank @Size(max = 255) String contentType,
        @NotNull Long sizeBytes,
        @Size(max = 255) String checksum,
        UUID ownerUserId,
        @Size(max = 255) String context,
        @Size(max = 255) String contextRef,
        Integer width,
        Integer height,
        Integer durationSeconds,
        @Size(max = 255) String perceptualHash,
        Instant exifTimestamp,
        BigDecimal exifLatitude,
        BigDecimal exifLongitude,
        @Size(max = 255) String publicUrl,
        @NotNull Boolean virusScanned,
        @NotNull Boolean virusClean,
        @NotNull MediaAsset.Status status) {
}
