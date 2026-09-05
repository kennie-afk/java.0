package com.smartseason.telemetryingest.web;

import com.smartseason.telemetryingest.platform.PageResponse;
import com.smartseason.telemetryingest.service.TelemetryAnomalyRecordService;
import com.smartseason.telemetryingest.web.dto.TelemetryAnomalyRecordCreateRequest;
import com.smartseason.telemetryingest.web.dto.TelemetryAnomalyRecordResponse;
import com.smartseason.telemetryingest.web.dto.TelemetryAnomalyRecordUpdateRequest;
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
@RequestMapping("/api/telemetry-ingest/v1/telemetry-anomalies")
@Tag(name = "TelemetryAnomalyRecord", description = "Raw + downsampled device telemetry, anomaly emission (highest write volume)")
public class TelemetryAnomalyRecordController {

    private final TelemetryAnomalyRecordService service;

    public TelemetryAnomalyRecordController(TelemetryAnomalyRecordService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(summary = "List telemetry-anomalies for the caller's tenant")
    public PageResponse<TelemetryAnomalyRecordResponse> list(@PageableDefault(size = 20) Pageable pageable) {
        return service.list(pageable);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Fetch a single TelemetryAnomalyRecord by id")
    public TelemetryAnomalyRecordResponse get(@PathVariable UUID id) {
        return service.get(id);
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'OPERATOR')")
    @Operation(summary = "Create a TelemetryAnomalyRecord")
    public ResponseEntity<TelemetryAnomalyRecordResponse> create(@Valid @RequestBody TelemetryAnomalyRecordCreateRequest request) {
        TelemetryAnomalyRecordResponse created = service.create(request);
        return ResponseEntity.created(URI.create("/api/telemetry-ingest/v1/telemetry-anomalies/" + created.id())).body(created);
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'OPERATOR')")
    @Operation(summary = "Apply a partial update to a TelemetryAnomalyRecord")
    public TelemetryAnomalyRecordResponse update(@PathVariable UUID id, @Valid @RequestBody TelemetryAnomalyRecordUpdateRequest request) {
        return service.update(id, request);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    @Operation(summary = "Delete a TelemetryAnomalyRecord")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
