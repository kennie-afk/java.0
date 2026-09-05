package com.smartseason.catalog.web.dto;

import com.smartseason.catalog.domain.ProductVariant;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.UUID;

public record ProductVariantUpdateRequest(
        UUID productId,
        @Size(max = 255) String sku,
        @Size(max = 255) String variantName,
        BigDecimal packSize,
        @Size(max = 255) String packUnit,
        @Size(max = 255) String grade,
        Boolean active) {
}
