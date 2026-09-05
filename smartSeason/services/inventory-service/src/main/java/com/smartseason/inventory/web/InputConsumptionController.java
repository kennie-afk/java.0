package com.smartseason.inventory.web;

import com.smartseason.inventory.platform.PageResponse;
import com.smartseason.inventory.service.InputConsumptionService;
import com.smartseason.inventory.web.dto.InputConsumptionCreateRequest;
import com.smartseason.inventory.web.dto.InputConsumptionResponse;
import com.smartseason.inventory.web.dto.InputConsumptionUpdateRequest;
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
@RequestMapping("/api/inventory/v1/input-consumptions")
@Tag(name = "InputConsumption", description = "Aggregation-centre stock, batches, grading, reservations, farm-input reconciliation")
public class InputConsumptionController {

    private final InputConsumptionService service;

    public InputConsumptionController(InputConsumptionService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(summary = "List input-consumptions for the caller's tenant")
    public PageResponse<InputConsumptionResponse> list(@PageableDefault(size = 20) Pageable pageable) {
        return service.list(pageable);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Fetch a single InputConsumption by id")
    public InputConsumptionResponse get(@PathVariable UUID id) {
        return service.get(id);
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'OPERATOR')")
    @Operation(summary = "Create a InputConsumption")
    public ResponseEntity<InputConsumptionResponse> create(@Valid @RequestBody InputConsumptionCreateRequest request) {
        InputConsumptionResponse created = service.create(request);
        return ResponseEntity.created(URI.create("/api/inventory/v1/input-consumptions/" + created.id())).body(created);
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'OPERATOR')")
    @Operation(summary = "Apply a partial update to a InputConsumption")
    public InputConsumptionResponse update(@PathVariable UUID id, @Valid @RequestBody InputConsumptionUpdateRequest request) {
        return service.update(id, request);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    @Operation(summary = "Delete a InputConsumption")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
