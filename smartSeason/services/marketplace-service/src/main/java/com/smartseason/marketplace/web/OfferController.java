package com.smartseason.marketplace.web;

import com.smartseason.marketplace.platform.PageResponse;
import com.smartseason.marketplace.service.OfferService;
import com.smartseason.marketplace.web.dto.OfferCreateRequest;
import com.smartseason.marketplace.web.dto.OfferResponse;
import com.smartseason.marketplace.web.dto.OfferUpdateRequest;
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
@RequestMapping("/api/marketplace/v1/offers")
@Tag(name = "Offer", description = "Supply listings, demand posts, offers, buyer-seller matching")
public class OfferController {

    private final OfferService service;

    public OfferController(OfferService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'FARMER', 'MANAGER', 'BUYER')")
    @Operation(summary = "List offers for the caller's tenant")
    public PageResponse<OfferResponse> list(@PageableDefault(size = 20) Pageable pageable) {
        return service.list(pageable);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'FARMER', 'MANAGER', 'BUYER')")
    @Operation(summary = "Fetch a single Offer by id")
    public OfferResponse get(@PathVariable UUID id) {
        return service.get(id);
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'FARMER', 'BUYER')")
    @Operation(summary = "Create a Offer")
    public ResponseEntity<OfferResponse> create(@Valid @RequestBody OfferCreateRequest request) {
        OfferResponse created = service.create(request);
        return ResponseEntity.created(URI.create("/api/marketplace/v1/offers/" + created.id())).body(created);
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'FARMER', 'BUYER')")
    @Operation(summary = "Apply a partial update to a Offer")
    public OfferResponse update(@PathVariable UUID id, @Valid @RequestBody OfferUpdateRequest request) {
        return service.update(id, request);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'FARMER', 'BUYER')")
    @Operation(summary = "Delete a Offer")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
