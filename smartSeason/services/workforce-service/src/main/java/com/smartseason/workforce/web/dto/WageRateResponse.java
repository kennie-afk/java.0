package com.smartseason.workforce.web.dto;

import com.smartseason.workforce.domain.WageRate;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record WageRateResponse(
        UUID id,
        UUID farmId,
        String taskCode,
        WageRate.RateType rateType,
        BigDecimal amount,
        String currency,
        String unit,
        LocalDate effectiveFrom,
        LocalDate effectiveTo,
        Instant createdAt,
        Instant updatedAt) {

    public static WageRateResponse from(WageRate entity) {
        return new WageRateResponse(
                entity.getId(),
                entity.getFarmId(),
                entity.getTaskCode(),
                entity.getRateType(),
                entity.getAmount(),
                entity.getCurrency(),
                entity.getUnit(),
                entity.getEffectiveFrom(),
                entity.getEffectiveTo(),
                entity.getCreatedAt(),
                entity.getUpdatedAt());
    }
}
