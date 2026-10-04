package com.smartseason.farm.web.dto;

import com.smartseason.farm.domain.MilkDelivery;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record MilkDeliveryResponse(
        UUID id,
        UUID farmId,
        LocalDate deliveredOn,
        String buyerName,
        String receiptNo,
        BigDecimal litresDelivered,
        BigDecimal litresRejected,
        BigDecimal fatPct,
        BigDecimal snfPct,
        BigDecimal temperatureC,
        Boolean alcoholTestPassed,
        BigDecimal pricePerLitre,
        String currency,
        MilkDelivery.Status status,
        String notes,
        Instant createdAt,
        Instant updatedAt) {

    public static MilkDeliveryResponse from(MilkDelivery entity) {
        return new MilkDeliveryResponse(
                entity.getId(),
                entity.getFarmId(),
                entity.getDeliveredOn(),
                entity.getBuyerName(),
                entity.getReceiptNo(),
                entity.getLitresDelivered(),
                entity.getLitresRejected(),
                entity.getFatPct(),
                entity.getSnfPct(),
                entity.getTemperatureC(),
                entity.getAlcoholTestPassed(),
                entity.getPricePerLitre(),
                entity.getCurrency(),
                entity.getStatus(),
                entity.getNotes(),
                entity.getCreatedAt(),
                entity.getUpdatedAt());
    }
}
