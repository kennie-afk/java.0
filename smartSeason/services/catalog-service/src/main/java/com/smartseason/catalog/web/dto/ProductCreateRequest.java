package com.smartseason.catalog.web.dto;

import com.smartseason.catalog.domain.Product;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ProductCreateRequest(
        @NotBlank @Size(max = 255) String commodityCode,
        @NotBlank @Size(max = 255) String name,
        String description,
        @Size(max = 255) String defaultGrade,
        @NotNull Product.Status status) {
}
