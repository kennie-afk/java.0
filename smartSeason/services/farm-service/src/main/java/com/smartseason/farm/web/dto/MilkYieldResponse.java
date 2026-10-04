package com.smartseason.farm.web.dto;

import com.smartseason.farm.domain.MilkYield;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record MilkYieldResponse(
        UUID id,
        UUID cowId,
        UUID farmId,
        LocalDate recordedOn,
        MilkYield.Session session,
        BigDecimal litres,
        UUID recordedBy,
        String notes,
        Instant createdAt,
        Instant updatedAt) {

    public static MilkYieldResponse from(MilkYield entity) {
        return new MilkYieldResponse(
                entity.getId(),
                entity.getCowId(),
                entity.getFarmId(),
                entity.getRecordedOn(),
                entity.getSession(),
                entity.getLitres(),
                entity.getRecordedBy(),
                entity.getNotes(),
                entity.getCreatedAt(),
                entity.getUpdatedAt());
    }
}
