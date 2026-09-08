package com.smartseason.analytics.web;

import com.smartseason.analytics.platform.PageResponse;
import com.smartseason.analytics.service.ReportService;
import com.smartseason.analytics.web.dto.ReportCreateRequest;
import com.smartseason.analytics.web.dto.ReportResponse;
import com.smartseason.analytics.web.dto.ReportUpdateRequest;
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
@RequestMapping("/api/analytics/v1/reports")
@Tag(name = "Report", description = "Event sink, data marts, dashboards API, scheduled reports, exports")
public class ReportController {

    private final ReportService service;

    public ReportController(ReportService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'FARMER', 'MANAGER', 'AGRONOMIST', 'FINANCE')")
    @Operation(summary = "List reports for the caller's tenant")
    public PageResponse<ReportResponse> list(@PageableDefault(size = 20) Pageable pageable) {
        return service.list(pageable);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'FARMER', 'MANAGER', 'AGRONOMIST', 'FINANCE')")
    @Operation(summary = "Fetch a single Report by id")
    public ReportResponse get(@PathVariable UUID id) {
        return service.get(id);
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN')")
    @Operation(summary = "Create a Report")
    public ResponseEntity<ReportResponse> create(@Valid @RequestBody ReportCreateRequest request) {
        ReportResponse created = service.create(request);
        return ResponseEntity.created(URI.create("/api/analytics/v1/reports/" + created.id())).body(created);
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN')")
    @Operation(summary = "Apply a partial update to a Report")
    public ReportResponse update(@PathVariable UUID id, @Valid @RequestBody ReportUpdateRequest request) {
        return service.update(id, request);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN')")
    @Operation(summary = "Delete a Report")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
