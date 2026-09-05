package com.smartseason.analytics.web;

import com.smartseason.analytics.platform.PageResponse;
import com.smartseason.analytics.service.ReportRunService;
import com.smartseason.analytics.web.dto.ReportRunCreateRequest;
import com.smartseason.analytics.web.dto.ReportRunResponse;
import com.smartseason.analytics.web.dto.ReportRunUpdateRequest;
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
@RequestMapping("/api/analytics/v1/report-runs")
@Tag(name = "ReportRun", description = "Event sink, data marts, dashboards API, scheduled reports, exports")
public class ReportRunController {

    private final ReportRunService service;

    public ReportRunController(ReportRunService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(summary = "List report-runs for the caller's tenant")
    public PageResponse<ReportRunResponse> list(@PageableDefault(size = 20) Pageable pageable) {
        return service.list(pageable);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Fetch a single ReportRun by id")
    public ReportRunResponse get(@PathVariable UUID id) {
        return service.get(id);
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'OPERATOR')")
    @Operation(summary = "Create a ReportRun")
    public ResponseEntity<ReportRunResponse> create(@Valid @RequestBody ReportRunCreateRequest request) {
        ReportRunResponse created = service.create(request);
        return ResponseEntity.created(URI.create("/api/analytics/v1/report-runs/" + created.id())).body(created);
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'OPERATOR')")
    @Operation(summary = "Apply a partial update to a ReportRun")
    public ReportRunResponse update(@PathVariable UUID id, @Valid @RequestBody ReportRunUpdateRequest request) {
        return service.update(id, request);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    @Operation(summary = "Delete a ReportRun")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
