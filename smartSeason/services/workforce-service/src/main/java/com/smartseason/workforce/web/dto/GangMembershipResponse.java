package com.smartseason.workforce.web.dto;

import com.smartseason.workforce.domain.GangMembership;
import java.time.Instant;
import java.util.UUID;

public record GangMembershipResponse(
        UUID id,
        UUID gangId,
        UUID workerId,
        Instant joinedAt,
        Instant leftAt,
        GangMembership.Role role,
        Instant createdAt,
        Instant updatedAt) {

    public static GangMembershipResponse from(GangMembership entity) {
        return new GangMembershipResponse(
                entity.getId(),
                entity.getGangId(),
                entity.getWorkerId(),
                entity.getJoinedAt(),
                entity.getLeftAt(),
                entity.getRole(),
                entity.getCreatedAt(),
                entity.getUpdatedAt());
    }
}
