package com.smartseason.season.web;

import com.smartseason.season.platform.PageResponse;
import com.smartseason.season.service.PlantingPlanService;
import com.smartseason.season.web.dto.PlantingPlanCreateRequest;
import com.smartseason.season.web.dto.PlantingPlanResponse;
import com.smartseason.season.web.dto.PlantingPlanUpdateRequest;
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
@RequestMapping("/api/season/v1/planting-plans")
@Tag(name = "PlantingPlan", description = "Crop cycles per plot, stage calendars, planting plans, yields")
public class PlantingPlanController {

    private final PlantingPlanService service;

    public PlantingPlanController(PlantingPlanService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(summary = "List planting-plans for the caller's tenant")
    public PageResponse<PlantingPlanResponse> list(@PageableDefault(size = 20) Pageable pageable) {
        return service.list(pageable);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Fetch a single PlantingPlan by id")
    public PlantingPlanResponse get(@PathVariable UUID id) {
        return service.get(id);
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'OPERATOR')")
    @Operation(summary = "Create a PlantingPlan")
    public ResponseEntity<PlantingPlanResponse> create(@Valid @RequestBody PlantingPlanCreateRequest request) {
        PlantingPlanResponse created = service.create(request);
        return ResponseEntity.created(URI.create("/api/season/v1/planting-plans/" + created.id())).body(created);
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'OPERATOR')")
    @Operation(summary = "Apply a partial update to a PlantingPlan")
    public PlantingPlanResponse update(@PathVariable UUID id, @Valid @RequestBody PlantingPlanUpdateRequest request) {
        return service.update(id, request);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    @Operation(summary = "Delete a PlantingPlan")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
