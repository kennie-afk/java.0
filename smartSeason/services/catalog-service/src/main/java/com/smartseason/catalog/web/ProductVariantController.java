package com.smartseason.catalog.web;

import com.smartseason.catalog.platform.PageResponse;
import com.smartseason.catalog.service.ProductVariantService;
import com.smartseason.catalog.web.dto.ProductVariantCreateRequest;
import com.smartseason.catalog.web.dto.ProductVariantResponse;
import com.smartseason.catalog.web.dto.ProductVariantUpdateRequest;
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
@RequestMapping("/api/catalog/v1/product-variants")
@Tag(name = "ProductVariant", description = "Produce taxonomy, products, variants, grading standards, certifications")
public class ProductVariantController {

    private final ProductVariantService service;

    public ProductVariantController(ProductVariantService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'FARMER', 'MANAGER', 'AGRONOMIST', 'STOREKEEPER', 'BUYER')")
    @Operation(summary = "List product-variants for the caller's tenant")
    public PageResponse<ProductVariantResponse> list(@PageableDefault(size = 20) Pageable pageable) {
        return service.list(pageable);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'FARMER', 'MANAGER', 'AGRONOMIST', 'STOREKEEPER', 'BUYER')")
    @Operation(summary = "Fetch a single ProductVariant by id")
    public ProductVariantResponse get(@PathVariable UUID id) {
        return service.get(id);
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN')")
    @Operation(summary = "Create a ProductVariant")
    public ResponseEntity<ProductVariantResponse> create(@Valid @RequestBody ProductVariantCreateRequest request) {
        ProductVariantResponse created = service.create(request);
        return ResponseEntity.created(URI.create("/api/catalog/v1/product-variants/" + created.id())).body(created);
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN')")
    @Operation(summary = "Apply a partial update to a ProductVariant")
    public ProductVariantResponse update(@PathVariable UUID id, @Valid @RequestBody ProductVariantUpdateRequest request) {
        return service.update(id, request);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN')")
    @Operation(summary = "Delete a ProductVariant")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
