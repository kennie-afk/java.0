package com.smartseason.automation.web;

import com.smartseason.automation.platform.PageResponse;
import com.smartseason.automation.service.DigitalTwinService;
import com.smartseason.automation.web.dto.DigitalTwinCreateRequest;
import com.smartseason.automation.web.dto.DigitalTwinResponse;
import com.smartseason.automation.web.dto.DigitalTwinUpdateRequest;
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
@RequestMapping("/api/automation/v1/digital-twins")
@Tag(name = "DigitalTwin", description = "Automation rules, actuator command pipeline, safety interlocks, digital twin")
public class DigitalTwinController {

    private final DigitalTwinService service;

    public DigitalTwinController(DigitalTwinService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'FARMER', 'MANAGER', 'AGRONOMIST')")
    @Operation(summary = "List digital-twins for the caller's tenant")
    public PageResponse<DigitalTwinResponse> list(@PageableDefault(size = 20) Pageable pageable) {
        return service.list(pageable);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'FARMER', 'MANAGER', 'AGRONOMIST')")
    @Operation(summary = "Fetch a single DigitalTwin by id")
    public DigitalTwinResponse get(@PathVariable UUID id) {
        return service.get(id);
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'FARMER', 'MANAGER')")
    @Operation(summary = "Create a DigitalTwin")
    public ResponseEntity<DigitalTwinResponse> create(@Valid @RequestBody DigitalTwinCreateRequest request) {
        DigitalTwinResponse created = service.create(request);
        return ResponseEntity.created(URI.create("/api/automation/v1/digital-twins/" + created.id())).body(created);
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'FARMER', 'MANAGER')")
    @Operation(summary = "Apply a partial update to a DigitalTwin")
    public DigitalTwinResponse update(@PathVariable UUID id, @Valid @RequestBody DigitalTwinUpdateRequest request) {
        return service.update(id, request);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN')")
    @Operation(summary = "Delete a DigitalTwin")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
