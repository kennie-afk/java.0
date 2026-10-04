package com.smartseason.farm.web.dto;

import com.smartseason.farm.domain.BreedingEvent;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.UUID;

public record BreedingEventCreateRequest(
        @NotNull UUID cowId,
        @NotNull UUID farmId,
        @NotNull LocalDate eventDate,
        @NotNull BreedingEvent.EventType eventType,
        BreedingEvent.Method method,
        @Size(max = 255) String sireRef,
        @Size(max = 255) String outcome,
        LocalDate expectedCalvingOn,
        String notes) {
}
