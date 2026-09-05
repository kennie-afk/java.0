package com.smartseason.pricing.web;

import com.smartseason.pricing.platform.PageResponse;
import com.smartseason.pricing.service.MarketIndexService;
import com.smartseason.pricing.web.dto.MarketIndexCreateRequest;
import com.smartseason.pricing.web.dto.MarketIndexResponse;
import com.smartseason.pricing.web.dto.MarketIndexUpdateRequest;
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
@RequestMapping("/api/pricing/v1/market-indices")
@Tag(name = "MarketIndex", description = "Reference prices, market index per commodity/region, price series, suggestions")
public class MarketIndexController {

    private final MarketIndexService service;

    public MarketIndexController(MarketIndexService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(summary = "List market-indices for the caller's tenant")
    public PageResponse<MarketIndexResponse> list(@PageableDefault(size = 20) Pageable pageable) {
        return service.list(pageable);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Fetch a single MarketIndex by id")
    public MarketIndexResponse get(@PathVariable UUID id) {
        return service.get(id);
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'OPERATOR')")
    @Operation(summary = "Create a MarketIndex")
    public ResponseEntity<MarketIndexResponse> create(@Valid @RequestBody MarketIndexCreateRequest request) {
        MarketIndexResponse created = service.create(request);
        return ResponseEntity.created(URI.create("/api/pricing/v1/market-indices/" + created.id())).body(created);
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'OPERATOR')")
    @Operation(summary = "Apply a partial update to a MarketIndex")
    public MarketIndexResponse update(@PathVariable UUID id, @Valid @RequestBody MarketIndexUpdateRequest request) {
        return service.update(id, request);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    @Operation(summary = "Delete a MarketIndex")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
