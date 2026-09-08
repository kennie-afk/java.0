package com.smartseason.inventory.web;

import com.smartseason.inventory.platform.PageResponse;
import com.smartseason.inventory.service.GradingResultService;
import com.smartseason.inventory.web.dto.GradingResultCreateRequest;
import com.smartseason.inventory.web.dto.GradingResultResponse;
import com.smartseason.inventory.web.dto.GradingResultUpdateRequest;
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
@RequestMapping("/api/inventory/v1/grading-results")
@Tag(name = "GradingResult", description = "Aggregation-centre stock, batches, grading, reservations, farm-input reconciliation")
public class GradingResultController {

    private final GradingResultService service;

    public GradingResultController(GradingResultService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'FARMER', 'MANAGER', 'STOREKEEPER')")
    @Operation(summary = "List grading-results for the caller's tenant")
    public PageResponse<GradingResultResponse> list(@PageableDefault(size = 20) Pageable pageable) {
        return service.list(pageable);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'FARMER', 'MANAGER', 'STOREKEEPER')")
    @Operation(summary = "Fetch a single GradingResult by id")
    public GradingResultResponse get(@PathVariable UUID id) {
        return service.get(id);
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'STOREKEEPER')")
    @Operation(summary = "Create a GradingResult")
    public ResponseEntity<GradingResultResponse> create(@Valid @RequestBody GradingResultCreateRequest request) {
        GradingResultResponse created = service.create(request);
        return ResponseEntity.created(URI.create("/api/inventory/v1/grading-results/" + created.id())).body(created);
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'STOREKEEPER')")
    @Operation(summary = "Apply a partial update to a GradingResult")
    public GradingResultResponse update(@PathVariable UUID id, @Valid @RequestBody GradingResultUpdateRequest request) {
        return service.update(id, request);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'STOREKEEPER')")
    @Operation(summary = "Delete a GradingResult")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
