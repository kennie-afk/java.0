package com.smartseason.attendance.web;

import com.smartseason.attendance.platform.PageResponse;
import com.smartseason.attendance.service.GeofenceService;
import com.smartseason.attendance.web.dto.GeofenceCreateRequest;
import com.smartseason.attendance.web.dto.GeofenceResponse;
import com.smartseason.attendance.web.dto.GeofenceUpdateRequest;
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
@RequestMapping("/api/attendance/v1/geofences")
@Tag(name = "Geofence", description = "Geofenced biometric clock-in/out, shifts, piece-rate tallies, offline sync")
public class GeofenceController {

    private final GeofenceService service;

    public GeofenceController(GeofenceService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(summary = "List geofences for the caller's tenant")
    public PageResponse<GeofenceResponse> list(@PageableDefault(size = 20) Pageable pageable) {
        return service.list(pageable);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Fetch a single Geofence by id")
    public GeofenceResponse get(@PathVariable UUID id) {
        return service.get(id);
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'OPERATOR')")
    @Operation(summary = "Create a Geofence")
    public ResponseEntity<GeofenceResponse> create(@Valid @RequestBody GeofenceCreateRequest request) {
        GeofenceResponse created = service.create(request);
        return ResponseEntity.created(URI.create("/api/attendance/v1/geofences/" + created.id())).body(created);
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'OPERATOR')")
    @Operation(summary = "Apply a partial update to a Geofence")
    public GeofenceResponse update(@PathVariable UUID id, @Valid @RequestBody GeofenceUpdateRequest request) {
        return service.update(id, request);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    @Operation(summary = "Delete a Geofence")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
