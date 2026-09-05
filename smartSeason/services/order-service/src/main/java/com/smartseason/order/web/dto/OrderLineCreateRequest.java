package com.smartseason.order.web.dto;

import com.smartseason.order.domain.OrderLine;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.UUID;

public record OrderLineCreateRequest(
        @NotNull UUID orderId,
        UUID listingId,
        @NotBlank @Size(max = 255) String commodityCode,
        @Size(max = 255) String grade,
        @NotNull BigDecimal quantity,
        @NotBlank @Size(max = 255) String unit,
        @NotNull BigDecimal unitPrice,
        @NotNull BigDecimal lineTotal,
        UUID batchId,
        @NotNull BigDecimal fulfilledQuantity) {
}
