package com.smartseason.media.web.dto;

import com.smartseason.media.domain.MediaVariant;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record MediaVariantCreateRequest(
        @NotNull UUID assetId,
        @NotBlank @Size(max = 255) String variantName,
        @NotBlank @Size(max = 255) String storageKey,
        Integer width,
        Integer height,
        Long sizeBytes,
        @Size(max = 255) String contentType,
        @Size(max = 255) String publicUrl) {
}
