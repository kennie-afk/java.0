package com.smartseason.farm.web.dto;

import com.smartseason.farm.domain.FarmMembership;
import java.time.Instant;
import java.util.UUID;

public record FarmMembershipResponse(
        UUID id,
        UUID farmId,
        UUID userId,
        FarmMembership.Role role,
        UUID invitedBy,
        Instant acceptedAt,
        FarmMembership.Status status,
        Instant createdAt,
        Instant updatedAt) {

    public static FarmMembershipResponse from(FarmMembership entity) {
        return new FarmMembershipResponse(
                entity.getId(),
                entity.getFarmId(),
                entity.getUserId(),
                entity.getRole(),
                entity.getInvitedBy(),
                entity.getAcceptedAt(),
                entity.getStatus(),
                entity.getCreatedAt(),
                entity.getUpdatedAt());
    }
}
