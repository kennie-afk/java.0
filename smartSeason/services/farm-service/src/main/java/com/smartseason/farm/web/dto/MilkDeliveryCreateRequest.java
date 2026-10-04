package com.smartseason.farm.web.dto;

import com.smartseason.farm.domain.MilkDelivery;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record MilkDeliveryCreateRequest(
        @NotNull UUID farmId,
        @NotNull LocalDate deliveredOn,
        @NotBlank @Size(max = 255) String buyerName,
        @Size(max = 255) String receiptNo,
        @NotNull BigDecimal litresDelivered,
        @NotNull BigDecimal litresRejected,
        BigDecimal fatPct,
        BigDecimal snfPct,
        BigDecimal temperatureC,
        Boolean alcoholTestPassed,
        BigDecimal pricePerLitre,
        @Size(max = 255) String currency,
        @NotNull MilkDelivery.Status status,
        String notes) {
}
