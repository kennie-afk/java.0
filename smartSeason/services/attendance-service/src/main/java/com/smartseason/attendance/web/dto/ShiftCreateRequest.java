package com.smartseason.attendance.web.dto;

import com.smartseason.attendance.domain.Shift;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;

public record ShiftCreateRequest(
        @NotNull UUID workerId,
        @NotNull UUID farmId,
        UUID gangId,
        @NotNull Instant startedAt,
        Instant endedAt,
        Integer durationMinutes,
        @NotNull Integer breakMinutes,
        UUID supervisorId,
        @NotNull Shift.Status status,
        @Size(max = 255) String anomalyFlags) {
}
