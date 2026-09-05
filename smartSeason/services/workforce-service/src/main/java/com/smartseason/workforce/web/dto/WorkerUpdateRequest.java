package com.smartseason.workforce.web.dto;

import com.smartseason.workforce.domain.Worker;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record WorkerUpdateRequest(
        @Size(max = 255) String nationalId,
        @Size(max = 255) String fullName,
        @Size(max = 255) String phone,
        Worker.Gender gender,
        LocalDate dateOfBirth,
        UUID farmId,
        @Size(max = 255) String payoutPhone,
        @Size(max = 255) String payoutAccount,
        @Size(max = 255) String biometricRef,
        Worker.Status status,
        Integer riskScore,
        Instant onboardedAt,
        @Size(max = 255) String photoUrl) {
}
