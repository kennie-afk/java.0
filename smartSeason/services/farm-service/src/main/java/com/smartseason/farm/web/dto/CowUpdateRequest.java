package com.smartseason.farm.web.dto;

import com.smartseason.farm.domain.Cow;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.UUID;

public record CowUpdateRequest(
        UUID farmId,
        @Size(max = 255) String tagNo,
        @Size(max = 255) String name,
        @Size(max = 255) String breed,
        Cow.Sex sex,
        LocalDate birthDate,
        UUID damId,
        @Size(max = 255) String sireRef,
        Cow.Status status,
        LocalDate acquiredOn,
        LocalDate exitedOn,
        String notes) {
}
