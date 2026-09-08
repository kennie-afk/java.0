package com.smartseason.workforce.web;

import com.smartseason.workforce.platform.PageResponse;
import com.smartseason.workforce.service.WageRateService;
import com.smartseason.workforce.web.dto.WageRateCreateRequest;
import com.smartseason.workforce.web.dto.WageRateResponse;
import com.smartseason.workforce.web.dto.WageRateUpdateRequest;
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
@RequestMapping("/api/workforce/v1/wage-rates")
@Tag(name = "WageRate", description = "Workers, contracts, wage rates, gangs, supervisors, farm assignment")
public class WageRateController {

    private final WageRateService service;

    public WageRateController(WageRateService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'FARMER', 'MANAGER', 'FINANCE')")
    @Operation(summary = "List wage-rates for the caller's tenant")
    public PageResponse<WageRateResponse> list(@PageableDefault(size = 20) Pageable pageable) {
        return service.list(pageable);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'FARMER', 'MANAGER', 'FINANCE')")
    @Operation(summary = "Fetch a single WageRate by id")
    public WageRateResponse get(@PathVariable UUID id) {
        return service.get(id);
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'FARMER')")
    @Operation(summary = "Create a WageRate")
    public ResponseEntity<WageRateResponse> create(@Valid @RequestBody WageRateCreateRequest request) {
        WageRateResponse created = service.create(request);
        return ResponseEntity.created(URI.create("/api/workforce/v1/wage-rates/" + created.id())).body(created);
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'FARMER')")
    @Operation(summary = "Apply a partial update to a WageRate")
    public WageRateResponse update(@PathVariable UUID id, @Valid @RequestBody WageRateUpdateRequest request) {
        return service.update(id, request);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'FARMER')")
    @Operation(summary = "Delete a WageRate")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
