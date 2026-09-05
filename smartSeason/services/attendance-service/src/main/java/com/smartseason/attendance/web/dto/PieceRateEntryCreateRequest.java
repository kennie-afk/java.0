package com.smartseason.attendance.web.dto;

import com.smartseason.attendance.domain.PieceRateEntry;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record PieceRateEntryCreateRequest(
        @NotNull UUID workerId,
        UUID shiftId,
        @NotNull UUID farmId,
        UUID plotId,
        @NotBlank @Size(max = 255) String taskCode,
        @NotNull BigDecimal quantity,
        @NotBlank @Size(max = 255) String unit,
        @NotNull Instant recordedAt,
        UUID recordedBy,
        @Size(max = 255) String weighStationId,
        UUID verifiedBy,
        Instant verifiedAt,
        @NotNull PieceRateEntry.Status status) {
}
