package com.smartseason.attendance.web.dto;

import com.smartseason.attendance.domain.Shift;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;

public record ShiftUpdateRequest(
        UUID workerId,
        UUID farmId,
        UUID gangId,
        Instant startedAt,
        Instant endedAt,
        Integer durationMinutes,
        Integer breakMinutes,
        UUID supervisorId,
        Shift.Status status,
        @Size(max = 255) String anomalyFlags) {
}
