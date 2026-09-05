package com.smartseason.agronomy.web;

import com.smartseason.agronomy.platform.PageResponse;
import com.smartseason.agronomy.service.ScoutingReportService;
import com.smartseason.agronomy.web.dto.ScoutingReportCreateRequest;
import com.smartseason.agronomy.web.dto.ScoutingReportResponse;
import com.smartseason.agronomy.web.dto.ScoutingReportUpdateRequest;
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
@RequestMapping("/api/agronomy/v1/scouting-reports")
@Tag(name = "ScoutingReport", description = "Advisories, pest/disease library, scouting reports, crop playbooks")
public class ScoutingReportController {

    private final ScoutingReportService service;

    public ScoutingReportController(ScoutingReportService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(summary = "List scouting-reports for the caller's tenant")
    public PageResponse<ScoutingReportResponse> list(@PageableDefault(size = 20) Pageable pageable) {
        return service.list(pageable);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Fetch a single ScoutingReport by id")
    public ScoutingReportResponse get(@PathVariable UUID id) {
        return service.get(id);
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'OPERATOR')")
    @Operation(summary = "Create a ScoutingReport")
    public ResponseEntity<ScoutingReportResponse> create(@Valid @RequestBody ScoutingReportCreateRequest request) {
        ScoutingReportResponse created = service.create(request);
        return ResponseEntity.created(URI.create("/api/agronomy/v1/scouting-reports/" + created.id())).body(created);
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'OPERATOR')")
    @Operation(summary = "Apply a partial update to a ScoutingReport")
    public ScoutingReportResponse update(@PathVariable UUID id, @Valid @RequestBody ScoutingReportUpdateRequest request) {
        return service.update(id, request);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    @Operation(summary = "Delete a ScoutingReport")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
