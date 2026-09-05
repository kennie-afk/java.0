package com.smartseason.analytics.web;

import com.smartseason.analytics.platform.PageResponse;
import com.smartseason.analytics.service.DashboardWidgetService;
import com.smartseason.analytics.web.dto.DashboardWidgetCreateRequest;
import com.smartseason.analytics.web.dto.DashboardWidgetResponse;
import com.smartseason.analytics.web.dto.DashboardWidgetUpdateRequest;
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
@RequestMapping("/api/analytics/v1/dashboard-widgets")
@Tag(name = "DashboardWidget", description = "Event sink, data marts, dashboards API, scheduled reports, exports")
public class DashboardWidgetController {

    private final DashboardWidgetService service;

    public DashboardWidgetController(DashboardWidgetService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(summary = "List dashboard-widgets for the caller's tenant")
    public PageResponse<DashboardWidgetResponse> list(@PageableDefault(size = 20) Pageable pageable) {
        return service.list(pageable);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Fetch a single DashboardWidget by id")
    public DashboardWidgetResponse get(@PathVariable UUID id) {
        return service.get(id);
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'OPERATOR')")
    @Operation(summary = "Create a DashboardWidget")
    public ResponseEntity<DashboardWidgetResponse> create(@Valid @RequestBody DashboardWidgetCreateRequest request) {
        DashboardWidgetResponse created = service.create(request);
        return ResponseEntity.created(URI.create("/api/analytics/v1/dashboard-widgets/" + created.id())).body(created);
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'OPERATOR')")
    @Operation(summary = "Apply a partial update to a DashboardWidget")
    public DashboardWidgetResponse update(@PathVariable UUID id, @Valid @RequestBody DashboardWidgetUpdateRequest request) {
        return service.update(id, request);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    @Operation(summary = "Delete a DashboardWidget")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
