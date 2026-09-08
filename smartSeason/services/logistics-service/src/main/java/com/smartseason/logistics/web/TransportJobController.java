package com.smartseason.logistics.web;

import com.smartseason.logistics.platform.PageResponse;
import com.smartseason.logistics.service.TransportJobService;
import com.smartseason.logistics.web.dto.TransportJobCreateRequest;
import com.smartseason.logistics.web.dto.TransportJobResponse;
import com.smartseason.logistics.web.dto.TransportJobUpdateRequest;
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
@RequestMapping("/api/logistics/v1/transport-jobs")
@Tag(name = "TransportJob", description = "Transport jobs, driver/vehicle assignment, routing, cold chain, proof of delivery")
public class TransportJobController {

    private final TransportJobService service;

    public TransportJobController(TransportJobService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'FARMER', 'MANAGER', 'STOREKEEPER', 'BUYER')")
    @Operation(summary = "List transport-jobs for the caller's tenant")
    public PageResponse<TransportJobResponse> list(@PageableDefault(size = 20) Pageable pageable) {
        return service.list(pageable);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'FARMER', 'MANAGER', 'STOREKEEPER', 'BUYER')")
    @Operation(summary = "Fetch a single TransportJob by id")
    public TransportJobResponse get(@PathVariable UUID id) {
        return service.get(id);
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'STOREKEEPER')")
    @Operation(summary = "Create a TransportJob")
    public ResponseEntity<TransportJobResponse> create(@Valid @RequestBody TransportJobCreateRequest request) {
        TransportJobResponse created = service.create(request);
        return ResponseEntity.created(URI.create("/api/logistics/v1/transport-jobs/" + created.id())).body(created);
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'STOREKEEPER')")
    @Operation(summary = "Apply a partial update to a TransportJob")
    public TransportJobResponse update(@PathVariable UUID id, @Valid @RequestBody TransportJobUpdateRequest request) {
        return service.update(id, request);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN')")
    @Operation(summary = "Delete a TransportJob")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
