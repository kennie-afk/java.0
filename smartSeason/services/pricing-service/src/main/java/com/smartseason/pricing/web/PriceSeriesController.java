package com.smartseason.pricing.web;

import com.smartseason.pricing.platform.PageResponse;
import com.smartseason.pricing.service.PriceSeriesService;
import com.smartseason.pricing.web.dto.PriceSeriesCreateRequest;
import com.smartseason.pricing.web.dto.PriceSeriesResponse;
import com.smartseason.pricing.web.dto.PriceSeriesUpdateRequest;
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
@RequestMapping("/api/pricing/v1/price-series")
@Tag(name = "PriceSeries", description = "Reference prices, market index per commodity/region, price series, suggestions")
public class PriceSeriesController {

    private final PriceSeriesService service;

    public PriceSeriesController(PriceSeriesService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'FARMER', 'MANAGER', 'BUYER')")
    @Operation(summary = "List price-series for the caller's tenant")
    public PageResponse<PriceSeriesResponse> list(@PageableDefault(size = 20) Pageable pageable) {
        return service.list(pageable);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'FARMER', 'MANAGER', 'BUYER')")
    @Operation(summary = "Fetch a single PriceSeries by id")
    public PriceSeriesResponse get(@PathVariable UUID id) {
        return service.get(id);
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN')")
    @Operation(summary = "Create a PriceSeries")
    public ResponseEntity<PriceSeriesResponse> create(@Valid @RequestBody PriceSeriesCreateRequest request) {
        PriceSeriesResponse created = service.create(request);
        return ResponseEntity.created(URI.create("/api/pricing/v1/price-series/" + created.id())).body(created);
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN')")
    @Operation(summary = "Apply a partial update to a PriceSeries")
    public PriceSeriesResponse update(@PathVariable UUID id, @Valid @RequestBody PriceSeriesUpdateRequest request) {
        return service.update(id, request);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN')")
    @Operation(summary = "Delete a PriceSeries")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
