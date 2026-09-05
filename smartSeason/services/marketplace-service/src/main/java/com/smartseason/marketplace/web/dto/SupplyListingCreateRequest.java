package com.smartseason.marketplace.web.dto;

import com.smartseason.marketplace.domain.SupplyListing;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record SupplyListingCreateRequest(
        @NotNull UUID sellerOrgId,
        UUID farmId,
        @NotBlank @Size(max = 255) String commodityCode,
        @Size(max = 255) String variety,
        @Size(max = 255) String grade,
        @NotNull BigDecimal quantity,
        @NotBlank @Size(max = 255) String unit,
        @NotNull BigDecimal askPrice,
        @NotBlank @Size(max = 255) String currency,
        LocalDate availableFrom,
        LocalDate availableTo,
        @Size(max = 255) String county,
        BigDecimal latitude,
        BigDecimal longitude,
        UUID batchId,
        String photoUrls,
        String description,
        @NotNull SupplyListing.Status status) {
}
