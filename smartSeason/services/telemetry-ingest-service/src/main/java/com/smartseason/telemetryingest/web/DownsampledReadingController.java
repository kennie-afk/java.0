package com.smartseason.telemetryingest.web;

import com.smartseason.telemetryingest.platform.PageResponse;
import com.smartseason.telemetryingest.service.DownsampledReadingService;
import com.smartseason.telemetryingest.web.dto.DownsampledReadingCreateRequest;
import com.smartseason.telemetryingest.web.dto.DownsampledReadingResponse;
import com.smartseason.telemetryingest.web.dto.DownsampledReadingUpdateRequest;
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
@RequestMapping("/api/telemetry-ingest/v1/downsampled-readings")
@Tag(name = "DownsampledReading", description = "Raw + downsampled device telemetry, anomaly emission (highest write volume)")
public class DownsampledReadingController {

    private final DownsampledReadingService service;

    public DownsampledReadingController(DownsampledReadingService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'FARMER', 'MANAGER', 'AGRONOMIST')")
    @Operation(summary = "List downsampled-readings for the caller's tenant")
    public PageResponse<DownsampledReadingResponse> list(@PageableDefault(size = 20) Pageable pageable) {
        return service.list(pageable);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'FARMER', 'MANAGER', 'AGRONOMIST')")
    @Operation(summary = "Fetch a single DownsampledReading by id")
    public DownsampledReadingResponse get(@PathVariable UUID id) {
        return service.get(id);
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN')")
    @Operation(summary = "Create a DownsampledReading")
    public ResponseEntity<DownsampledReadingResponse> create(@Valid @RequestBody DownsampledReadingCreateRequest request) {
        DownsampledReadingResponse created = service.create(request);
        return ResponseEntity.created(URI.create("/api/telemetry-ingest/v1/downsampled-readings/" + created.id())).body(created);
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN')")
    @Operation(summary = "Apply a partial update to a DownsampledReading")
    public DownsampledReadingResponse update(@PathVariable UUID id, @Valid @RequestBody DownsampledReadingUpdateRequest request) {
        return service.update(id, request);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN')")
    @Operation(summary = "Delete a DownsampledReading")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
