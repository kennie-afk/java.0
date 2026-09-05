package com.smartseason.marketplace.web.dto;

import com.smartseason.marketplace.domain.DemandPost;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record DemandPostCreateRequest(
        @NotNull UUID buyerOrgId,
        @NotBlank @Size(max = 255) String commodityCode,
        @Size(max = 255) String grade,
        @NotNull BigDecimal quantity,
        @NotBlank @Size(max = 255) String unit,
        BigDecimal bidPrice,
        @NotBlank @Size(max = 255) String currency,
        LocalDate neededBy,
        @Size(max = 255) String deliveryCounty,
        @NotNull Boolean recurring,
        @NotNull DemandPost.Status status,
        String notes) {
}
