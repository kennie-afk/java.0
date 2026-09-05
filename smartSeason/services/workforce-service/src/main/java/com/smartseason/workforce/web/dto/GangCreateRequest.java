package com.smartseason.workforce.web.dto;

import com.smartseason.workforce.domain.Gang;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record GangCreateRequest(
        @NotBlank @Size(max = 255) String name,
        @NotNull UUID farmId,
        UUID supervisorId,
        Integer targetSize,
        @NotNull Gang.Status status,
        String notes) {
}
