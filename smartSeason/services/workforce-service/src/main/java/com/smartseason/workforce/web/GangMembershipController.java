package com.smartseason.workforce.web;

import com.smartseason.workforce.platform.PageResponse;
import com.smartseason.workforce.service.GangMembershipService;
import com.smartseason.workforce.web.dto.GangMembershipCreateRequest;
import com.smartseason.workforce.web.dto.GangMembershipResponse;
import com.smartseason.workforce.web.dto.GangMembershipUpdateRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/workforce/v1/gang-memberships")
@Tag(name = "GangMembership", description = "Workers, contracts, wage rates, gangs, supervisors, farm assignment")
public class GangMembershipController {

    private final GangMembershipService service;

    public GangMembershipController(GangMembershipService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(summary = "List gang-memberships for the caller's tenant")
    public PageResponse<GangMembershipResponse> list(@PageableDefault(size = 20) Pageable pageable) {
        return service.list(pageable);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Fetch a single GangMembership by id")
    public GangMembershipResponse get(@PathVariable UUID id) {
        return service.get(id);
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'OPERATOR')")
    @Operation(summary = "Create a GangMembership")
    public ResponseEntity<GangMembershipResponse> create(@Valid @RequestBody GangMembershipCreateRequest request) {
        GangMembershipResponse created = service.create(request);
        return ResponseEntity.created(URI.create("/api/workforce/v1/gang-memberships/" + created.id())).body(created);
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'OPERATOR')")
    @Operation(summary = "Apply a partial update to a GangMembership")
    public GangMembershipResponse update(@PathVariable UUID id, @Valid @RequestBody GangMembershipUpdateRequest request) {
        return service.update(id, request);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    @Operation(summary = "Delete a GangMembership")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
