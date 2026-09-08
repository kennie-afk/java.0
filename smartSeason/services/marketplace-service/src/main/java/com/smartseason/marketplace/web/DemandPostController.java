package com.smartseason.marketplace.web;

import com.smartseason.marketplace.platform.PageResponse;
import com.smartseason.marketplace.service.DemandPostService;
import com.smartseason.marketplace.web.dto.DemandPostCreateRequest;
import com.smartseason.marketplace.web.dto.DemandPostResponse;
import com.smartseason.marketplace.web.dto.DemandPostUpdateRequest;
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
@RequestMapping("/api/marketplace/v1/demand-posts")
@Tag(name = "DemandPost", description = "Supply listings, demand posts, offers, buyer-seller matching")
public class DemandPostController {

    private final DemandPostService service;

    public DemandPostController(DemandPostService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'FARMER', 'MANAGER', 'BUYER')")
    @Operation(summary = "List demand-posts for the caller's tenant")
    public PageResponse<DemandPostResponse> list(@PageableDefault(size = 20) Pageable pageable) {
        return service.list(pageable);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'FARMER', 'MANAGER', 'BUYER')")
    @Operation(summary = "Fetch a single DemandPost by id")
    public DemandPostResponse get(@PathVariable UUID id) {
        return service.get(id);
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'FARMER', 'BUYER')")
    @Operation(summary = "Create a DemandPost")
    public ResponseEntity<DemandPostResponse> create(@Valid @RequestBody DemandPostCreateRequest request) {
        DemandPostResponse created = service.create(request);
        return ResponseEntity.created(URI.create("/api/marketplace/v1/demand-posts/" + created.id())).body(created);
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'FARMER', 'BUYER')")
    @Operation(summary = "Apply a partial update to a DemandPost")
    public DemandPostResponse update(@PathVariable UUID id, @Valid @RequestBody DemandPostUpdateRequest request) {
        return service.update(id, request);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'FARMER', 'BUYER')")
    @Operation(summary = "Delete a DemandPost")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
