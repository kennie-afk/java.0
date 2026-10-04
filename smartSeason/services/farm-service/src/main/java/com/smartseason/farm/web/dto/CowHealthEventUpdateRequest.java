package com.smartseason.farm.web.dto;

import com.smartseason.farm.domain.CowHealthEvent;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record CowHealthEventUpdateRequest(
        UUID cowId,
        UUID farmId,
        LocalDate eventDate,
        CowHealthEvent.EventType eventType,
        String description,
        @Size(max = 255) String medicine,
        LocalDate withdrawalEndsOn,
        @Size(max = 255) String vetName,
        BigDecimal costAmount) {
}
