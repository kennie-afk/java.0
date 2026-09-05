package com.smartseason.marketplace.web.dto;

import com.smartseason.marketplace.domain.SupplyListing;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record SupplyListingUpdateRequest(
        UUID sellerOrgId,
        UUID farmId,
        @Size(max = 255) String commodityCode,
        @Size(max = 255) String variety,
        @Size(max = 255) String grade,
        BigDecimal quantity,
        @Size(max = 255) String unit,
        BigDecimal askPrice,
        @Size(max = 255) String currency,
        LocalDate availableFrom,
        LocalDate availableTo,
        @Size(max = 255) String county,
        BigDecimal latitude,
        BigDecimal longitude,
        UUID batchId,
        String photoUrls,
        String description,
        SupplyListing.Status status) {
}
