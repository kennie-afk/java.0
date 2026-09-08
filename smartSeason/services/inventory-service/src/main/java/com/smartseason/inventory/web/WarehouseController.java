package com.smartseason.inventory.web;

import com.smartseason.inventory.platform.PageResponse;
import com.smartseason.inventory.service.WarehouseService;
import com.smartseason.inventory.web.dto.WarehouseCreateRequest;
import com.smartseason.inventory.web.dto.WarehouseResponse;
import com.smartseason.inventory.web.dto.WarehouseUpdateRequest;
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
@RequestMapping("/api/inventory/v1/warehouses")
@Tag(name = "Warehouse", description = "Aggregation-centre stock, batches, grading, reservations, farm-input reconciliation")
public class WarehouseController {

    private final WarehouseService service;

    public WarehouseController(WarehouseService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'FARMER', 'MANAGER', 'STOREKEEPER')")
    @Operation(summary = "List warehouses for the caller's tenant")
    public PageResponse<WarehouseResponse> list(@PageableDefault(size = 20) Pageable pageable) {
        return service.list(pageable);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'FARMER', 'MANAGER', 'STOREKEEPER')")
    @Operation(summary = "Fetch a single Warehouse by id")
    public WarehouseResponse get(@PathVariable UUID id) {
        return service.get(id);
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'STOREKEEPER')")
    @Operation(summary = "Create a Warehouse")
    public ResponseEntity<WarehouseResponse> create(@Valid @RequestBody WarehouseCreateRequest request) {
        WarehouseResponse created = service.create(request);
        return ResponseEntity.created(URI.create("/api/inventory/v1/warehouses/" + created.id())).body(created);
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'STOREKEEPER')")
    @Operation(summary = "Apply a partial update to a Warehouse")
    public WarehouseResponse update(@PathVariable UUID id, @Valid @RequestBody WarehouseUpdateRequest request) {
        return service.update(id, request);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'STOREKEEPER')")
    @Operation(summary = "Delete a Warehouse")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
