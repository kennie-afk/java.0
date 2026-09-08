package com.smartseason.farm.web;

import com.smartseason.farm.platform.PageResponse;
import com.smartseason.farm.service.FarmMembershipService;
import com.smartseason.farm.web.dto.FarmMembershipCreateRequest;
import com.smartseason.farm.web.dto.FarmMembershipResponse;
import com.smartseason.farm.web.dto.FarmMembershipUpdateRequest;
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
@RequestMapping("/api/farm/v1/farm-memberships")
@Tag(name = "FarmMembership", description = "Farms, plots, geo boundaries, soil profiles, cooperative membership")
public class FarmMembershipController {

    private final FarmMembershipService service;

    public FarmMembershipController(FarmMembershipService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'FARMER', 'MANAGER', 'AGRONOMIST')")
    @Operation(summary = "List farm-memberships for the caller's tenant")
    public PageResponse<FarmMembershipResponse> list(@PageableDefault(size = 20) Pageable pageable) {
        return service.list(pageable);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'FARMER', 'MANAGER', 'AGRONOMIST')")
    @Operation(summary = "Fetch a single FarmMembership by id")
    public FarmMembershipResponse get(@PathVariable UUID id) {
        return service.get(id);
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'FARMER', 'MANAGER')")
    @Operation(summary = "Create a FarmMembership")
    public ResponseEntity<FarmMembershipResponse> create(@Valid @RequestBody FarmMembershipCreateRequest request) {
        FarmMembershipResponse created = service.create(request);
        return ResponseEntity.created(URI.create("/api/farm/v1/farm-memberships/" + created.id())).body(created);
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'FARMER', 'MANAGER')")
    @Operation(summary = "Apply a partial update to a FarmMembership")
    public FarmMembershipResponse update(@PathVariable UUID id, @Valid @RequestBody FarmMembershipUpdateRequest request) {
        return service.update(id, request);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'FARMER')")
    @Operation(summary = "Delete a FarmMembership")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
