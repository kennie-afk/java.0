package com.smartseason.agronomy.web;

import com.smartseason.agronomy.platform.PageResponse;
import com.smartseason.agronomy.service.AdvisoryService;
import com.smartseason.agronomy.web.dto.AdvisoryCreateRequest;
import com.smartseason.agronomy.web.dto.AdvisoryResponse;
import com.smartseason.agronomy.web.dto.AdvisoryUpdateRequest;
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
@RequestMapping("/api/agronomy/v1/advisories")
@Tag(name = "Advisory", description = "Advisories, pest/disease library, scouting reports, crop playbooks")
public class AdvisoryController {

    private final AdvisoryService service;

    public AdvisoryController(AdvisoryService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(summary = "List advisories for the caller's tenant")
    public PageResponse<AdvisoryResponse> list(@PageableDefault(size = 20) Pageable pageable) {
        return service.list(pageable);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Fetch a single Advisory by id")
    public AdvisoryResponse get(@PathVariable UUID id) {
        return service.get(id);
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'OPERATOR')")
    @Operation(summary = "Create a Advisory")
    public ResponseEntity<AdvisoryResponse> create(@Valid @RequestBody AdvisoryCreateRequest request) {
        AdvisoryResponse created = service.create(request);
        return ResponseEntity.created(URI.create("/api/agronomy/v1/advisories/" + created.id())).body(created);
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'OPERATOR')")
    @Operation(summary = "Apply a partial update to a Advisory")
    public AdvisoryResponse update(@PathVariable UUID id, @Valid @RequestBody AdvisoryUpdateRequest request) {
        return service.update(id, request);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    @Operation(summary = "Delete a Advisory")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
