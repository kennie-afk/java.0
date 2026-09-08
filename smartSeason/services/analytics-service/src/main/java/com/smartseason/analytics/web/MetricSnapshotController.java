package com.smartseason.analytics.web;

import com.smartseason.analytics.platform.PageResponse;
import com.smartseason.analytics.service.MetricSnapshotService;
import com.smartseason.analytics.web.dto.MetricSnapshotCreateRequest;
import com.smartseason.analytics.web.dto.MetricSnapshotResponse;
import com.smartseason.analytics.web.dto.MetricSnapshotUpdateRequest;
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
@RequestMapping("/api/analytics/v1/metric-snapshots")
@Tag(name = "MetricSnapshot", description = "Event sink, data marts, dashboards API, scheduled reports, exports")
public class MetricSnapshotController {

    private final MetricSnapshotService service;

    public MetricSnapshotController(MetricSnapshotService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'FARMER', 'MANAGER', 'AGRONOMIST', 'FINANCE')")
    @Operation(summary = "List metric-snapshots for the caller's tenant")
    public PageResponse<MetricSnapshotResponse> list(@PageableDefault(size = 20) Pageable pageable) {
        return service.list(pageable);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'FARMER', 'MANAGER', 'AGRONOMIST', 'FINANCE')")
    @Operation(summary = "Fetch a single MetricSnapshot by id")
    public MetricSnapshotResponse get(@PathVariable UUID id) {
        return service.get(id);
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN')")
    @Operation(summary = "Create a MetricSnapshot")
    public ResponseEntity<MetricSnapshotResponse> create(@Valid @RequestBody MetricSnapshotCreateRequest request) {
        MetricSnapshotResponse created = service.create(request);
        return ResponseEntity.created(URI.create("/api/analytics/v1/metric-snapshots/" + created.id())).body(created);
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN')")
    @Operation(summary = "Apply a partial update to a MetricSnapshot")
    public MetricSnapshotResponse update(@PathVariable UUID id, @Valid @RequestBody MetricSnapshotUpdateRequest request) {
        return service.update(id, request);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN')")
    @Operation(summary = "Delete a MetricSnapshot")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
