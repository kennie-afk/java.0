package com.smartseason.season.web;

import com.smartseason.season.platform.PageResponse;
import com.smartseason.season.service.StageTemplateService;
import com.smartseason.season.web.dto.StageTemplateCreateRequest;
import com.smartseason.season.web.dto.StageTemplateResponse;
import com.smartseason.season.web.dto.StageTemplateUpdateRequest;
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
@RequestMapping("/api/season/v1/stage-templates")
@Tag(name = "StageTemplate", description = "Crop cycles per plot, stage calendars, planting plans, yields")
public class StageTemplateController {

    private final StageTemplateService service;

    public StageTemplateController(StageTemplateService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(summary = "List stage-templates for the caller's tenant")
    public PageResponse<StageTemplateResponse> list(@PageableDefault(size = 20) Pageable pageable) {
        return service.list(pageable);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Fetch a single StageTemplate by id")
    public StageTemplateResponse get(@PathVariable UUID id) {
        return service.get(id);
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'OPERATOR')")
    @Operation(summary = "Create a StageTemplate")
    public ResponseEntity<StageTemplateResponse> create(@Valid @RequestBody StageTemplateCreateRequest request) {
        StageTemplateResponse created = service.create(request);
        return ResponseEntity.created(URI.create("/api/season/v1/stage-templates/" + created.id())).body(created);
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'OPERATOR')")
    @Operation(summary = "Apply a partial update to a StageTemplate")
    public StageTemplateResponse update(@PathVariable UUID id, @Valid @RequestBody StageTemplateUpdateRequest request) {
        return service.update(id, request);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    @Operation(summary = "Delete a StageTemplate")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
