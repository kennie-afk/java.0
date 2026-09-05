package com.smartseason.attendance.web.dto;

import com.smartseason.attendance.domain.PieceRateEntry;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record PieceRateEntryUpdateRequest(
        UUID workerId,
        UUID shiftId,
        UUID farmId,
        UUID plotId,
        @Size(max = 255) String taskCode,
        BigDecimal quantity,
        @Size(max = 255) String unit,
        Instant recordedAt,
        UUID recordedBy,
        @Size(max = 255) String weighStationId,
        UUID verifiedBy,
        Instant verifiedAt,
        PieceRateEntry.Status status) {
}
