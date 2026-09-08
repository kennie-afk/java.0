package com.smartseason.inventory.web;

import com.smartseason.inventory.platform.PageResponse;
import com.smartseason.inventory.service.InputIssueService;
import com.smartseason.inventory.web.dto.InputIssueCreateRequest;
import com.smartseason.inventory.web.dto.InputIssueResponse;
import com.smartseason.inventory.web.dto.InputIssueUpdateRequest;
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
@RequestMapping("/api/inventory/v1/input-issues")
@Tag(name = "InputIssue", description = "Aggregation-centre stock, batches, grading, reservations, farm-input reconciliation")
public class InputIssueController {

    private final InputIssueService service;

    public InputIssueController(InputIssueService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'FARMER', 'MANAGER', 'STOREKEEPER')")
    @Operation(summary = "List input-issues for the caller's tenant")
    public PageResponse<InputIssueResponse> list(@PageableDefault(size = 20) Pageable pageable) {
        return service.list(pageable);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'FARMER', 'MANAGER', 'STOREKEEPER')")
    @Operation(summary = "Fetch a single InputIssue by id")
    public InputIssueResponse get(@PathVariable UUID id) {
        return service.get(id);
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'STOREKEEPER')")
    @Operation(summary = "Create a InputIssue")
    public ResponseEntity<InputIssueResponse> create(@Valid @RequestBody InputIssueCreateRequest request) {
        InputIssueResponse created = service.create(request);
        return ResponseEntity.created(URI.create("/api/inventory/v1/input-issues/" + created.id())).body(created);
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'STOREKEEPER')")
    @Operation(summary = "Apply a partial update to a InputIssue")
    public InputIssueResponse update(@PathVariable UUID id, @Valid @RequestBody InputIssueUpdateRequest request) {
        return service.update(id, request);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'STOREKEEPER')")
    @Operation(summary = "Delete a InputIssue")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
