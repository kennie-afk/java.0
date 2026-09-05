package com.smartseason.season.web;

import com.smartseason.season.platform.PageResponse;
import com.smartseason.season.service.SeasonStageService;
import com.smartseason.season.web.dto.SeasonStageCreateRequest;
import com.smartseason.season.web.dto.SeasonStageResponse;
import com.smartseason.season.web.dto.SeasonStageUpdateRequest;
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
@RequestMapping("/api/season/v1/season-stages")
@Tag(name = "SeasonStage", description = "Crop cycles per plot, stage calendars, planting plans, yields")
public class SeasonStageController {

    private final SeasonStageService service;

    public SeasonStageController(SeasonStageService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(summary = "List season-stages for the caller's tenant")
    public PageResponse<SeasonStageResponse> list(@PageableDefault(size = 20) Pageable pageable) {
        return service.list(pageable);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Fetch a single SeasonStage by id")
    public SeasonStageResponse get(@PathVariable UUID id) {
        return service.get(id);
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'OPERATOR')")
    @Operation(summary = "Create a SeasonStage")
    public ResponseEntity<SeasonStageResponse> create(@Valid @RequestBody SeasonStageCreateRequest request) {
        SeasonStageResponse created = service.create(request);
        return ResponseEntity.created(URI.create("/api/season/v1/season-stages/" + created.id())).body(created);
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'OPERATOR')")
    @Operation(summary = "Apply a partial update to a SeasonStage")
    public SeasonStageResponse update(@PathVariable UUID id, @Valid @RequestBody SeasonStageUpdateRequest request) {
        return service.update(id, request);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    @Operation(summary = "Delete a SeasonStage")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
