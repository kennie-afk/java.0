package com.smartseason.logistics.web;

import com.smartseason.logistics.platform.PageResponse;
import com.smartseason.logistics.service.ColdChainReadingService;
import com.smartseason.logistics.web.dto.ColdChainReadingCreateRequest;
import com.smartseason.logistics.web.dto.ColdChainReadingResponse;
import com.smartseason.logistics.web.dto.ColdChainReadingUpdateRequest;
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
@RequestMapping("/api/logistics/v1/cold-chain-readings")
@Tag(name = "ColdChainReading", description = "Transport jobs, driver/vehicle assignment, routing, cold chain, proof of delivery")
public class ColdChainReadingController {

    private final ColdChainReadingService service;

    public ColdChainReadingController(ColdChainReadingService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'FARMER', 'MANAGER', 'STOREKEEPER', 'BUYER')")
    @Operation(summary = "List cold-chain-readings for the caller's tenant")
    public PageResponse<ColdChainReadingResponse> list(@PageableDefault(size = 20) Pageable pageable) {
        return service.list(pageable);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'FARMER', 'MANAGER', 'STOREKEEPER', 'BUYER')")
    @Operation(summary = "Fetch a single ColdChainReading by id")
    public ColdChainReadingResponse get(@PathVariable UUID id) {
        return service.get(id);
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'STOREKEEPER')")
    @Operation(summary = "Create a ColdChainReading")
    public ResponseEntity<ColdChainReadingResponse> create(@Valid @RequestBody ColdChainReadingCreateRequest request) {
        ColdChainReadingResponse created = service.create(request);
        return ResponseEntity.created(URI.create("/api/logistics/v1/cold-chain-readings/" + created.id())).body(created);
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'STOREKEEPER')")
    @Operation(summary = "Apply a partial update to a ColdChainReading")
    public ColdChainReadingResponse update(@PathVariable UUID id, @Valid @RequestBody ColdChainReadingUpdateRequest request) {
        return service.update(id, request);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN')")
    @Operation(summary = "Delete a ColdChainReading")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
