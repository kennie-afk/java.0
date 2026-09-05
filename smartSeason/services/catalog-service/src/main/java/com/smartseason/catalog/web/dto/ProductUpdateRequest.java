package com.smartseason.catalog.web.dto;

import com.smartseason.catalog.domain.Product;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ProductUpdateRequest(
        @Size(max = 255) String commodityCode,
        @Size(max = 255) String name,
        String description,
        @Size(max = 255) String defaultGrade,
        Product.Status status) {
}
