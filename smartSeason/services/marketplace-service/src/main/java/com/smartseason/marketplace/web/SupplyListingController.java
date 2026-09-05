package com.smartseason.marketplace.web;

import com.smartseason.marketplace.platform.PageResponse;
import com.smartseason.marketplace.service.SupplyListingService;
import com.smartseason.marketplace.web.dto.SupplyListingCreateRequest;
import com.smartseason.marketplace.web.dto.SupplyListingResponse;
import com.smartseason.marketplace.web.dto.SupplyListingUpdateRequest;
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
@RequestMapping("/api/marketplace/v1/supply-listings")
@Tag(name = "SupplyListing", description = "Supply listings, demand posts, offers, buyer-seller matching")
public class SupplyListingController {

    private final SupplyListingService service;

    public SupplyListingController(SupplyListingService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(summary = "List supply-listings for the caller's tenant")
    public PageResponse<SupplyListingResponse> list(@PageableDefault(size = 20) Pageable pageable) {
        return service.list(pageable);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Fetch a single SupplyListing by id")
    public SupplyListingResponse get(@PathVariable UUID id) {
        return service.get(id);
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'OPERATOR')")
    @Operation(summary = "Create a SupplyListing")
    public ResponseEntity<SupplyListingResponse> create(@Valid @RequestBody SupplyListingCreateRequest request) {
        SupplyListingResponse created = service.create(request);
        return ResponseEntity.created(URI.create("/api/marketplace/v1/supply-listings/" + created.id())).body(created);
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'OPERATOR')")
    @Operation(summary = "Apply a partial update to a SupplyListing")
    public SupplyListingResponse update(@PathVariable UUID id, @Valid @RequestBody SupplyListingUpdateRequest request) {
        return service.update(id, request);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    @Operation(summary = "Delete a SupplyListing")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
