package com.smartseason.identity.web;

import com.smartseason.identity.platform.PageResponse;
import com.smartseason.identity.service.OrganisationService;
import com.smartseason.identity.web.dto.OrganisationCreateRequest;
import com.smartseason.identity.web.dto.OrganisationResponse;
import com.smartseason.identity.web.dto.OrganisationUpdateRequest;
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
@RequestMapping("/api/identity/v1/organisations")
@Tag(name = "Organisation", description = "Users, organisations, roles, sessions, KYC, JWT issuance + JWKS")
public class OrganisationController {

    private final OrganisationService service;

    public OrganisationController(OrganisationService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(summary = "List organisations for the caller's tenant")
    public PageResponse<OrganisationResponse> list(@PageableDefault(size = 20) Pageable pageable) {
        return service.list(pageable);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Fetch a single Organisation by id")
    public OrganisationResponse get(@PathVariable UUID id) {
        return service.get(id);
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'OPERATOR')")
    @Operation(summary = "Create a Organisation")
    public ResponseEntity<OrganisationResponse> create(@Valid @RequestBody OrganisationCreateRequest request) {
        OrganisationResponse created = service.create(request);
        return ResponseEntity.created(URI.create("/api/identity/v1/organisations/" + created.id())).body(created);
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'OPERATOR')")
    @Operation(summary = "Apply a partial update to a Organisation")
    public OrganisationResponse update(@PathVariable UUID id, @Valid @RequestBody OrganisationUpdateRequest request) {
        return service.update(id, request);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    @Operation(summary = "Delete a Organisation")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
