package com.smartseason.attendance.web.dto;

import com.smartseason.attendance.domain.PieceRateEntry;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record PieceRateEntryResponse(
        UUID id,
        UUID workerId,
        UUID shiftId,
        UUID farmId,
        UUID plotId,
        String taskCode,
        BigDecimal quantity,
        String unit,
        Instant recordedAt,
        UUID recordedBy,
        String weighStationId,
        UUID verifiedBy,
        Instant verifiedAt,
        PieceRateEntry.Status status,
        Instant createdAt,
        Instant updatedAt) {

    public static PieceRateEntryResponse from(PieceRateEntry entity) {
        return new PieceRateEntryResponse(
                entity.getId(),
                entity.getWorkerId(),
                entity.getShiftId(),
                entity.getFarmId(),
                entity.getPlotId(),
                entity.getTaskCode(),
                entity.getQuantity(),
                entity.getUnit(),
                entity.getRecordedAt(),
                entity.getRecordedBy(),
                entity.getWeighStationId(),
                entity.getVerifiedBy(),
                entity.getVerifiedAt(),
                entity.getStatus(),
                entity.getCreatedAt(),
                entity.getUpdatedAt());
    }
}
