package com.smartseason.workforce.web.dto;

import com.smartseason.workforce.domain.Gang;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record GangUpdateRequest(
        @Size(max = 255) String name,
        UUID farmId,
        UUID supervisorId,
        Integer targetSize,
        Gang.Status status,
        String notes) {
}
