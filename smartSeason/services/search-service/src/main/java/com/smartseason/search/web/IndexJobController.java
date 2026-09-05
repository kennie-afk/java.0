package com.smartseason.search.web;

import com.smartseason.search.platform.PageResponse;
import com.smartseason.search.service.IndexJobService;
import com.smartseason.search.web.dto.IndexJobCreateRequest;
import com.smartseason.search.web.dto.IndexJobResponse;
import com.smartseason.search.web.dto.IndexJobUpdateRequest;
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
@RequestMapping("/api/search/v1/index-jobs")
@Tag(name = "IndexJob", description = "Search indexes kept fresh from domain events (listings, farms, workers, produce)")
public class IndexJobController {

    private final IndexJobService service;

    public IndexJobController(IndexJobService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(summary = "List index-jobs for the caller's tenant")
    public PageResponse<IndexJobResponse> list(@PageableDefault(size = 20) Pageable pageable) {
        return service.list(pageable);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Fetch a single IndexJob by id")
    public IndexJobResponse get(@PathVariable UUID id) {
        return service.get(id);
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'OPERATOR')")
    @Operation(summary = "Create a IndexJob")
    public ResponseEntity<IndexJobResponse> create(@Valid @RequestBody IndexJobCreateRequest request) {
        IndexJobResponse created = service.create(request);
        return ResponseEntity.created(URI.create("/api/search/v1/index-jobs/" + created.id())).body(created);
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'OPERATOR')")
    @Operation(summary = "Apply a partial update to a IndexJob")
    public IndexJobResponse update(@PathVariable UUID id, @Valid @RequestBody IndexJobUpdateRequest request) {
        return service.update(id, request);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    @Operation(summary = "Delete a IndexJob")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
