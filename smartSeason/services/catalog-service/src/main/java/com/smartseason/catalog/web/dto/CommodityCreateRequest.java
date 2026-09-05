package com.smartseason.catalog.web.dto;

import com.smartseason.catalog.domain.Commodity;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CommodityCreateRequest(
        @NotBlank @Size(max = 255) String code,
        @NotBlank @Size(max = 255) String name,
        @Size(max = 255) String category,
        @NotBlank @Size(max = 255) String defaultUnit,
        @NotNull Boolean perishable,
        Integer shelfLifeDays,
        @Size(max = 255) String imageUrl) {
}
