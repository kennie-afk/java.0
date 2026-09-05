package com.smartseason.deviceregistry.web;

import com.smartseason.deviceregistry.platform.PageResponse;
import com.smartseason.deviceregistry.service.FirmwareReleaseService;
import com.smartseason.deviceregistry.web.dto.FirmwareReleaseCreateRequest;
import com.smartseason.deviceregistry.web.dto.FirmwareReleaseResponse;
import com.smartseason.deviceregistry.web.dto.FirmwareReleaseUpdateRequest;
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
@RequestMapping("/api/device-registry/v1/firmware-releases")
@Tag(name = "FirmwareRelease", description = "Device identity, provisioning, plot mapping, firmware, credentials")
public class FirmwareReleaseController {

    private final FirmwareReleaseService service;

    public FirmwareReleaseController(FirmwareReleaseService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(summary = "List firmware-releases for the caller's tenant")
    public PageResponse<FirmwareReleaseResponse> list(@PageableDefault(size = 20) Pageable pageable) {
        return service.list(pageable);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Fetch a single FirmwareRelease by id")
    public FirmwareReleaseResponse get(@PathVariable UUID id) {
        return service.get(id);
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'OPERATOR')")
    @Operation(summary = "Create a FirmwareRelease")
    public ResponseEntity<FirmwareReleaseResponse> create(@Valid @RequestBody FirmwareReleaseCreateRequest request) {
        FirmwareReleaseResponse created = service.create(request);
        return ResponseEntity.created(URI.create("/api/device-registry/v1/firmware-releases/" + created.id())).body(created);
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'OPERATOR')")
    @Operation(summary = "Apply a partial update to a FirmwareRelease")
    public FirmwareReleaseResponse update(@PathVariable UUID id, @Valid @RequestBody FirmwareReleaseUpdateRequest request) {
        return service.update(id, request);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    @Operation(summary = "Delete a FirmwareRelease")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
