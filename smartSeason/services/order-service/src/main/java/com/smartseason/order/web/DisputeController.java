package com.smartseason.order.web;

import com.smartseason.order.platform.PageResponse;
import com.smartseason.order.service.DisputeService;
import com.smartseason.order.web.dto.DisputeCreateRequest;
import com.smartseason.order.web.dto.DisputeResponse;
import com.smartseason.order.web.dto.DisputeUpdateRequest;
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
@RequestMapping("/api/order/v1/disputes")
@Tag(name = "Dispute", description = "Carts, orders, fulfillment saga, returns, disputes")
public class DisputeController {

    private final DisputeService service;

    public DisputeController(DisputeService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(summary = "List disputes for the caller's tenant")
    public PageResponse<DisputeResponse> list(@PageableDefault(size = 20) Pageable pageable) {
        return service.list(pageable);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Fetch a single Dispute by id")
    public DisputeResponse get(@PathVariable UUID id) {
        return service.get(id);
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'OPERATOR')")
    @Operation(summary = "Create a Dispute")
    public ResponseEntity<DisputeResponse> create(@Valid @RequestBody DisputeCreateRequest request) {
        DisputeResponse created = service.create(request);
        return ResponseEntity.created(URI.create("/api/order/v1/disputes/" + created.id())).body(created);
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'OPERATOR')")
    @Operation(summary = "Apply a partial update to a Dispute")
    public DisputeResponse update(@PathVariable UUID id, @Valid @RequestBody DisputeUpdateRequest request) {
        return service.update(id, request);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    @Operation(summary = "Delete a Dispute")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
