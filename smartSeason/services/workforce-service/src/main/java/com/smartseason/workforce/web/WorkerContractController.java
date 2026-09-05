package com.smartseason.workforce.web;

import com.smartseason.workforce.platform.PageResponse;
import com.smartseason.workforce.service.WorkerContractService;
import com.smartseason.workforce.web.dto.WorkerContractCreateRequest;
import com.smartseason.workforce.web.dto.WorkerContractResponse;
import com.smartseason.workforce.web.dto.WorkerContractUpdateRequest;
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
@RequestMapping("/api/workforce/v1/worker-contracts")
@Tag(name = "WorkerContract", description = "Workers, contracts, wage rates, gangs, supervisors, farm assignment")
public class WorkerContractController {

    private final WorkerContractService service;

    public WorkerContractController(WorkerContractService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(summary = "List worker-contracts for the caller's tenant")
    public PageResponse<WorkerContractResponse> list(@PageableDefault(size = 20) Pageable pageable) {
        return service.list(pageable);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Fetch a single WorkerContract by id")
    public WorkerContractResponse get(@PathVariable UUID id) {
        return service.get(id);
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'OPERATOR')")
    @Operation(summary = "Create a WorkerContract")
    public ResponseEntity<WorkerContractResponse> create(@Valid @RequestBody WorkerContractCreateRequest request) {
        WorkerContractResponse created = service.create(request);
        return ResponseEntity.created(URI.create("/api/workforce/v1/worker-contracts/" + created.id())).body(created);
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'OPERATOR')")
    @Operation(summary = "Apply a partial update to a WorkerContract")
    public WorkerContractResponse update(@PathVariable UUID id, @Valid @RequestBody WorkerContractUpdateRequest request) {
        return service.update(id, request);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    @Operation(summary = "Delete a WorkerContract")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
