package com.smartseason.deviceregistry.web;

import com.smartseason.deviceregistry.platform.PageResponse;
import com.smartseason.deviceregistry.service.DeviceCredentialService;
import com.smartseason.deviceregistry.web.dto.DeviceCredentialCreateRequest;
import com.smartseason.deviceregistry.web.dto.DeviceCredentialResponse;
import com.smartseason.deviceregistry.web.dto.DeviceCredentialUpdateRequest;
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
@RequestMapping("/api/device-registry/v1/device-credentials")
@Tag(name = "DeviceCredential", description = "Device identity, provisioning, plot mapping, firmware, credentials")
public class DeviceCredentialController {

    private final DeviceCredentialService service;

    public DeviceCredentialController(DeviceCredentialService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'FARMER', 'MANAGER')")
    @Operation(summary = "List device-credentials for the caller's tenant")
    public PageResponse<DeviceCredentialResponse> list(@PageableDefault(size = 20) Pageable pageable) {
        return service.list(pageable);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'FARMER', 'MANAGER')")
    @Operation(summary = "Fetch a single DeviceCredential by id")
    public DeviceCredentialResponse get(@PathVariable UUID id) {
        return service.get(id);
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN')")
    @Operation(summary = "Create a DeviceCredential")
    public ResponseEntity<DeviceCredentialResponse> create(@Valid @RequestBody DeviceCredentialCreateRequest request) {
        DeviceCredentialResponse created = service.create(request);
        return ResponseEntity.created(URI.create("/api/device-registry/v1/device-credentials/" + created.id())).body(created);
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN')")
    @Operation(summary = "Apply a partial update to a DeviceCredential")
    public DeviceCredentialResponse update(@PathVariable UUID id, @Valid @RequestBody DeviceCredentialUpdateRequest request) {
        return service.update(id, request);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN')")
    @Operation(summary = "Delete a DeviceCredential")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
