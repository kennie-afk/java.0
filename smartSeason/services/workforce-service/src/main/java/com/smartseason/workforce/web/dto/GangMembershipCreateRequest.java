package com.smartseason.workforce.web.dto;

import com.smartseason.workforce.domain.GangMembership;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;

public record GangMembershipCreateRequest(
        @NotNull UUID gangId,
        @NotNull UUID workerId,
        @NotNull Instant joinedAt,
        Instant leftAt,
        @NotNull GangMembership.Role role) {
}
