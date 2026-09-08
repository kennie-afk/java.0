package com.smartseason.logistics.web;

import com.smartseason.logistics.platform.PageResponse;
import com.smartseason.logistics.service.RouteStopService;
import com.smartseason.logistics.web.dto.RouteStopCreateRequest;
import com.smartseason.logistics.web.dto.RouteStopResponse;
import com.smartseason.logistics.web.dto.RouteStopUpdateRequest;
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
@RequestMapping("/api/logistics/v1/route-stops")
@Tag(name = "RouteStop", description = "Transport jobs, driver/vehicle assignment, routing, cold chain, proof of delivery")
public class RouteStopController {

    private final RouteStopService service;

    public RouteStopController(RouteStopService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'FARMER', 'MANAGER', 'STOREKEEPER', 'BUYER')")
    @Operation(summary = "List route-stops for the caller's tenant")
    public PageResponse<RouteStopResponse> list(@PageableDefault(size = 20) Pageable pageable) {
        return service.list(pageable);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'FARMER', 'MANAGER', 'STOREKEEPER', 'BUYER')")
    @Operation(summary = "Fetch a single RouteStop by id")
    public RouteStopResponse get(@PathVariable UUID id) {
        return service.get(id);
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'STOREKEEPER')")
    @Operation(summary = "Create a RouteStop")
    public ResponseEntity<RouteStopResponse> create(@Valid @RequestBody RouteStopCreateRequest request) {
        RouteStopResponse created = service.create(request);
        return ResponseEntity.created(URI.create("/api/logistics/v1/route-stops/" + created.id())).body(created);
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'STOREKEEPER')")
    @Operation(summary = "Apply a partial update to a RouteStop")
    public RouteStopResponse update(@PathVariable UUID id, @Valid @RequestBody RouteStopUpdateRequest request) {
        return service.update(id, request);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN')")
    @Operation(summary = "Delete a RouteStop")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
