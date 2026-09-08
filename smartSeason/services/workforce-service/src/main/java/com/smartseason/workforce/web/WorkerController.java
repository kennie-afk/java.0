package com.smartseason.workforce.web;

import com.smartseason.workforce.platform.PageResponse;
import com.smartseason.workforce.service.WorkerService;
import com.smartseason.workforce.web.dto.WorkerCreateRequest;
import com.smartseason.workforce.web.dto.WorkerResponse;
import com.smartseason.workforce.web.dto.WorkerUpdateRequest;
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
@RequestMapping("/api/workforce/v1/workers")
@Tag(name = "Worker", description = "Workers, contracts, wage rates, gangs, supervisors, farm assignment")
public class WorkerController {

    private final WorkerService service;

    public WorkerController(WorkerService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'FARMER', 'MANAGER', 'FINANCE')")
    @Operation(summary = "List workers for the caller's tenant")
    public PageResponse<WorkerResponse> list(@PageableDefault(size = 20) Pageable pageable) {
        return service.list(pageable);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'FARMER', 'MANAGER', 'FINANCE')")
    @Operation(summary = "Fetch a single Worker by id")
    public WorkerResponse get(@PathVariable UUID id) {
        return service.get(id);
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'FARMER')")
    @Operation(summary = "Create a Worker")
    public ResponseEntity<WorkerResponse> create(@Valid @RequestBody WorkerCreateRequest request) {
        WorkerResponse created = service.create(request);
        return ResponseEntity.created(URI.create("/api/workforce/v1/workers/" + created.id())).body(created);
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'FARMER')")
    @Operation(summary = "Apply a partial update to a Worker")
    public WorkerResponse update(@PathVariable UUID id, @Valid @RequestBody WorkerUpdateRequest request) {
        return service.update(id, request);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'FARMER')")
    @Operation(summary = "Delete a Worker")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
