package com.smartseason.media.web.dto;

import com.smartseason.media.domain.MediaVariant;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record MediaVariantUpdateRequest(
        UUID assetId,
        @Size(max = 255) String variantName,
        @Size(max = 255) String storageKey,
        Integer width,
        Integer height,
        Long sizeBytes,
        @Size(max = 255) String contentType,
        @Size(max = 255) String publicUrl) {
}
