package com.smartseason.traceability.web;

import com.smartseason.traceability.platform.PageResponse;
import com.smartseason.traceability.service.TraceBatchService;
import com.smartseason.traceability.web.dto.TraceBatchCreateRequest;
import com.smartseason.traceability.web.dto.TraceBatchResponse;
import com.smartseason.traceability.web.dto.TraceBatchUpdateRequest;
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
@RequestMapping("/api/traceability/v1/trace-batches")
@Tag(name = "TraceBatch", description = "Farm-to-fork lineage graph, certifications, QR pass")
public class TraceBatchController {

    private final TraceBatchService service;

    public TraceBatchController(TraceBatchService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'FARMER', 'MANAGER', 'AGRONOMIST', 'STOREKEEPER', 'BUYER')")
    @Operation(summary = "List trace-batches for the caller's tenant")
    public PageResponse<TraceBatchResponse> list(@PageableDefault(size = 20) Pageable pageable) {
        return service.list(pageable);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'FARMER', 'MANAGER', 'AGRONOMIST', 'STOREKEEPER', 'BUYER')")
    @Operation(summary = "Fetch a single TraceBatch by id")
    public TraceBatchResponse get(@PathVariable UUID id) {
        return service.get(id);
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'STOREKEEPER')")
    @Operation(summary = "Create a TraceBatch")
    public ResponseEntity<TraceBatchResponse> create(@Valid @RequestBody TraceBatchCreateRequest request) {
        TraceBatchResponse created = service.create(request);
        return ResponseEntity.created(URI.create("/api/traceability/v1/trace-batches/" + created.id())).body(created);
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'STOREKEEPER')")
    @Operation(summary = "Apply a partial update to a TraceBatch")
    public TraceBatchResponse update(@PathVariable UUID id, @Valid @RequestBody TraceBatchUpdateRequest request) {
        return service.update(id, request);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN')")
    @Operation(summary = "Delete a TraceBatch")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
