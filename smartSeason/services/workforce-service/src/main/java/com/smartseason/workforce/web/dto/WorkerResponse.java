package com.smartseason.workforce.web.dto;

import com.smartseason.workforce.domain.Worker;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record WorkerResponse(
        UUID id,
        UUID userId,
        String nationalId,
        String fullName,
        String phone,
        Worker.Gender gender,
        LocalDate dateOfBirth,
        UUID farmId,
        String payoutPhone,
        String payoutAccount,
        String biometricRef,
        Worker.Status status,
        Integer riskScore,
        Instant onboardedAt,
        String photoUrl,
        Instant createdAt,
        Instant updatedAt) {

    public static WorkerResponse from(Worker entity) {
        return new WorkerResponse(
                entity.getId(),
                entity.getUserId(),
                entity.getNationalId(),
                entity.getFullName(),
                entity.getPhone(),
                entity.getGender(),
                entity.getDateOfBirth(),
                entity.getFarmId(),
                entity.getPayoutPhone(),
                entity.getPayoutAccount(),
                entity.getBiometricRef(),
                entity.getStatus(),
                entity.getRiskScore(),
                entity.getOnboardedAt(),
                entity.getPhotoUrl(),
                entity.getCreatedAt(),
                entity.getUpdatedAt());
    }
}
