package com.smartseason.identity.team;

import com.smartseason.identity.team.TeamDtos.InviteRequest;
import com.smartseason.identity.team.TeamDtos.MemberResponse;
import com.smartseason.identity.team.TeamDtos.RoleChangeRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Under `/account/**`, which requires a token - unlike `/auth/**`, which is
 * permitAll so that signing in is possible.
 */
@RestController
@RequestMapping("/api/identity/v1/account/team")
@Tag(name = "Team", description = "People in the organisation and what they may do")
public class TeamController {

    private final TeamService service;

    public TeamController(TeamService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'FARMER')")
    @Operation(summary = "Everyone in the organisation")
    public List<MemberResponse> list() {
        return service.list();
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Add someone and set what they may do")
    public MemberResponse invite(@Valid @RequestBody InviteRequest request) {
        return service.invite(request);
    }

    @PatchMapping("/{id}/roles")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Change what someone may do")
    public MemberResponse setRoles(@PathVariable UUID id,
                                   @Valid @RequestBody RoleChangeRequest request) {
        return service.setRoles(id, request.roles());
    }
}
