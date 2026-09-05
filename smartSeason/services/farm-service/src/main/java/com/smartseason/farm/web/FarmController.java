package com.smartseason.farm.web;

import com.smartseason.farm.platform.PageResponse;
import com.smartseason.farm.service.FarmService;
import com.smartseason.farm.web.dto.FarmCreateRequest;
import com.smartseason.farm.web.dto.FarmResponse;
import com.smartseason.farm.web.dto.FarmUpdateRequest;
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
@RequestMapping("/api/farm/v1/farms")
@Tag(name = "Farm", description = "Farms, plots, geo boundaries, soil profiles, cooperative membership")
public class FarmController {

    private final FarmService service;

    public FarmController(FarmService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(summary = "List farms for the caller's tenant")
    public PageResponse<FarmResponse> list(@PageableDefault(size = 20) Pageable pageable) {
        return service.list(pageable);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Fetch a single Farm by id")
    public FarmResponse get(@PathVariable UUID id) {
        return service.get(id);
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'OPERATOR')")
    @Operation(summary = "Create a Farm")
    public ResponseEntity<FarmResponse> create(@Valid @RequestBody FarmCreateRequest request) {
        FarmResponse created = service.create(request);
        return ResponseEntity.created(URI.create("/api/farm/v1/farms/" + created.id())).body(created);
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'OPERATOR')")
    @Operation(summary = "Apply a partial update to a Farm")
    public FarmResponse update(@PathVariable UUID id, @Valid @RequestBody FarmUpdateRequest request) {
        return service.update(id, request);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    @Operation(summary = "Delete a Farm")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
