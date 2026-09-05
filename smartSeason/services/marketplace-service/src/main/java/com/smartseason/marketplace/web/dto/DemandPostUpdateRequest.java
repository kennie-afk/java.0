package com.smartseason.marketplace.web.dto;

import com.smartseason.marketplace.domain.DemandPost;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record DemandPostUpdateRequest(
        UUID buyerOrgId,
        @Size(max = 255) String commodityCode,
        @Size(max = 255) String grade,
        BigDecimal quantity,
        @Size(max = 255) String unit,
        BigDecimal bidPrice,
        @Size(max = 255) String currency,
        LocalDate neededBy,
        @Size(max = 255) String deliveryCounty,
        Boolean recurring,
        DemandPost.Status status,
        String notes) {
}
