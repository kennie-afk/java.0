package com.smartseason.logistics.web;

import com.smartseason.logistics.platform.PageResponse;
import com.smartseason.logistics.service.ProofOfDeliveryService;
import com.smartseason.logistics.web.dto.ProofOfDeliveryCreateRequest;
import com.smartseason.logistics.web.dto.ProofOfDeliveryResponse;
import com.smartseason.logistics.web.dto.ProofOfDeliveryUpdateRequest;
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
@RequestMapping("/api/logistics/v1/proofs-of-delivery")
@Tag(name = "ProofOfDelivery", description = "Transport jobs, driver/vehicle assignment, routing, cold chain, proof of delivery")
public class ProofOfDeliveryController {

    private final ProofOfDeliveryService service;

    public ProofOfDeliveryController(ProofOfDeliveryService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'FARMER', 'MANAGER', 'STOREKEEPER', 'BUYER')")
    @Operation(summary = "List proofs-of-delivery for the caller's tenant")
    public PageResponse<ProofOfDeliveryResponse> list(@PageableDefault(size = 20) Pageable pageable) {
        return service.list(pageable);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'FARMER', 'MANAGER', 'STOREKEEPER', 'BUYER')")
    @Operation(summary = "Fetch a single ProofOfDelivery by id")
    public ProofOfDeliveryResponse get(@PathVariable UUID id) {
        return service.get(id);
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'STOREKEEPER')")
    @Operation(summary = "Create a ProofOfDelivery")
    public ResponseEntity<ProofOfDeliveryResponse> create(@Valid @RequestBody ProofOfDeliveryCreateRequest request) {
        ProofOfDeliveryResponse created = service.create(request);
        return ResponseEntity.created(URI.create("/api/logistics/v1/proofs-of-delivery/" + created.id())).body(created);
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'STOREKEEPER')")
    @Operation(summary = "Apply a partial update to a ProofOfDelivery")
    public ProofOfDeliveryResponse update(@PathVariable UUID id, @Valid @RequestBody ProofOfDeliveryUpdateRequest request) {
        return service.update(id, request);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN')")
    @Operation(summary = "Delete a ProofOfDelivery")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
