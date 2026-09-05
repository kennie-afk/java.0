package com.smartseason.catalog.web.dto;

import com.smartseason.catalog.domain.Commodity;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CommodityUpdateRequest(
        @Size(max = 255) String code,
        @Size(max = 255) String name,
        @Size(max = 255) String category,
        @Size(max = 255) String defaultUnit,
        Boolean perishable,
        Integer shelfLifeDays,
        @Size(max = 255) String imageUrl) {
}
