package com.smartseason.identity.team;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

public final class TeamDtos {

    private TeamDtos() {
    }

    public record InviteRequest(
            @NotBlank @Email @Size(max = 255) String email,
            @NotBlank @Size(max = 255) String fullName,
            @Size(max = 32) String phone,
            @NotEmpty List<@NotBlank String> roles,
            @NotBlank @Size(min = 12, max = 200) String password) {
    }

    public record RoleChangeRequest(@NotEmpty List<@NotBlank String> roles) {
    }

    public record MemberResponse(
            UUID id,
            String email,
            String fullName,
            String phone,
            List<String> roles,
            String status) {
    }
}
