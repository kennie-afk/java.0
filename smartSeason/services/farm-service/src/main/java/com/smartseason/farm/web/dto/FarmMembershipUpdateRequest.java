package com.smartseason.farm.web.dto;

import com.smartseason.farm.domain.FarmMembership;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;

public record FarmMembershipUpdateRequest(
        UUID farmId,
        UUID userId,
        FarmMembership.Role role,
        UUID invitedBy,
        Instant acceptedAt,
        FarmMembership.Status status) {
}
