package com.smartseason.catalog.web;

import com.smartseason.catalog.platform.PageResponse;
import com.smartseason.catalog.service.GradeStandardService;
import com.smartseason.catalog.web.dto.GradeStandardCreateRequest;
import com.smartseason.catalog.web.dto.GradeStandardResponse;
import com.smartseason.catalog.web.dto.GradeStandardUpdateRequest;
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
@RequestMapping("/api/catalog/v1/grade-standards")
@Tag(name = "GradeStandard", description = "Produce taxonomy, products, variants, grading standards, certifications")
public class GradeStandardController {

    private final GradeStandardService service;

    public GradeStandardController(GradeStandardService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'FARMER', 'MANAGER', 'AGRONOMIST', 'STOREKEEPER', 'BUYER')")
    @Operation(summary = "List grade-standards for the caller's tenant")
    public PageResponse<GradeStandardResponse> list(@PageableDefault(size = 20) Pageable pageable) {
        return service.list(pageable);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'FARMER', 'MANAGER', 'AGRONOMIST', 'STOREKEEPER', 'BUYER')")
    @Operation(summary = "Fetch a single GradeStandard by id")
    public GradeStandardResponse get(@PathVariable UUID id) {
        return service.get(id);
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN')")
    @Operation(summary = "Create a GradeStandard")
    public ResponseEntity<GradeStandardResponse> create(@Valid @RequestBody GradeStandardCreateRequest request) {
        GradeStandardResponse created = service.create(request);
        return ResponseEntity.created(URI.create("/api/catalog/v1/grade-standards/" + created.id())).body(created);
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN')")
    @Operation(summary = "Apply a partial update to a GradeStandard")
    public GradeStandardResponse update(@PathVariable UUID id, @Valid @RequestBody GradeStandardUpdateRequest request) {
        return service.update(id, request);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN')")
    @Operation(summary = "Delete a GradeStandard")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
