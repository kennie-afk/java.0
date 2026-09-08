package com.smartseason.workforce.web;

import com.smartseason.workforce.platform.PageResponse;
import com.smartseason.workforce.service.GangService;
import com.smartseason.workforce.web.dto.GangCreateRequest;
import com.smartseason.workforce.web.dto.GangResponse;
import com.smartseason.workforce.web.dto.GangUpdateRequest;
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
@RequestMapping("/api/workforce/v1/gangs")
@Tag(name = "Gang", description = "Workers, contracts, wage rates, gangs, supervisors, farm assignment")
public class GangController {

    private final GangService service;

    public GangController(GangService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'FARMER', 'MANAGER', 'FINANCE')")
    @Operation(summary = "List gangs for the caller's tenant")
    public PageResponse<GangResponse> list(@PageableDefault(size = 20) Pageable pageable) {
        return service.list(pageable);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'FARMER', 'MANAGER', 'FINANCE')")
    @Operation(summary = "Fetch a single Gang by id")
    public GangResponse get(@PathVariable UUID id) {
        return service.get(id);
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'FARMER')")
    @Operation(summary = "Create a Gang")
    public ResponseEntity<GangResponse> create(@Valid @RequestBody GangCreateRequest request) {
        GangResponse created = service.create(request);
        return ResponseEntity.created(URI.create("/api/workforce/v1/gangs/" + created.id())).body(created);
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'FARMER')")
    @Operation(summary = "Apply a partial update to a Gang")
    public GangResponse update(@PathVariable UUID id, @Valid @RequestBody GangUpdateRequest request) {
        return service.update(id, request);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'FARMER')")
    @Operation(summary = "Delete a Gang")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
