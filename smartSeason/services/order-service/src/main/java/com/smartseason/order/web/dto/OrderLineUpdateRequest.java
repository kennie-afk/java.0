package com.smartseason.order.web.dto;

import com.smartseason.order.domain.OrderLine;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.UUID;

public record OrderLineUpdateRequest(
        UUID orderId,
        UUID listingId,
        @Size(max = 255) String commodityCode,
        @Size(max = 255) String grade,
        BigDecimal quantity,
        @Size(max = 255) String unit,
        BigDecimal unitPrice,
        BigDecimal lineTotal,
        UUID batchId,
        BigDecimal fulfilledQuantity) {
}
