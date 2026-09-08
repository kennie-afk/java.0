package com.smartseason.catalog.web;

import com.smartseason.catalog.platform.PageResponse;
import com.smartseason.catalog.service.CommodityService;
import com.smartseason.catalog.web.dto.CommodityCreateRequest;
import com.smartseason.catalog.web.dto.CommodityResponse;
import com.smartseason.catalog.web.dto.CommodityUpdateRequest;
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
@RequestMapping("/api/catalog/v1/commodities")
@Tag(name = "Commodity", description = "Produce taxonomy, products, variants, grading standards, certifications")
public class CommodityController {

    private final CommodityService service;

    public CommodityController(CommodityService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'FARMER', 'MANAGER', 'AGRONOMIST', 'STOREKEEPER', 'BUYER')")
    @Operation(summary = "List commodities for the caller's tenant")
    public PageResponse<CommodityResponse> list(@PageableDefault(size = 20) Pageable pageable) {
        return service.list(pageable);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'FARMER', 'MANAGER', 'AGRONOMIST', 'STOREKEEPER', 'BUYER')")
    @Operation(summary = "Fetch a single Commodity by id")
    public CommodityResponse get(@PathVariable UUID id) {
        return service.get(id);
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN')")
    @Operation(summary = "Create a Commodity")
    public ResponseEntity<CommodityResponse> create(@Valid @RequestBody CommodityCreateRequest request) {
        CommodityResponse created = service.create(request);
        return ResponseEntity.created(URI.create("/api/catalog/v1/commodities/" + created.id())).body(created);
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN')")
    @Operation(summary = "Apply a partial update to a Commodity")
    public CommodityResponse update(@PathVariable UUID id, @Valid @RequestBody CommodityUpdateRequest request) {
        return service.update(id, request);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN')")
    @Operation(summary = "Delete a Commodity")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
