package com.smartseason.automation.web;

import com.smartseason.automation.platform.PageResponse;
import com.smartseason.automation.service.SafetyInterlockService;
import com.smartseason.automation.web.dto.SafetyInterlockCreateRequest;
import com.smartseason.automation.web.dto.SafetyInterlockResponse;
import com.smartseason.automation.web.dto.SafetyInterlockUpdateRequest;
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
@RequestMapping("/api/automation/v1/safety-interlocks")
@Tag(name = "SafetyInterlock", description = "Automation rules, actuator command pipeline, safety interlocks, digital twin")
public class SafetyInterlockController {

    private final SafetyInterlockService service;

    public SafetyInterlockController(SafetyInterlockService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(summary = "List safety-interlocks for the caller's tenant")
    public PageResponse<SafetyInterlockResponse> list(@PageableDefault(size = 20) Pageable pageable) {
        return service.list(pageable);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Fetch a single SafetyInterlock by id")
    public SafetyInterlockResponse get(@PathVariable UUID id) {
        return service.get(id);
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'OPERATOR')")
    @Operation(summary = "Create a SafetyInterlock")
    public ResponseEntity<SafetyInterlockResponse> create(@Valid @RequestBody SafetyInterlockCreateRequest request) {
        SafetyInterlockResponse created = service.create(request);
        return ResponseEntity.created(URI.create("/api/automation/v1/safety-interlocks/" + created.id())).body(created);
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'OPERATOR')")
    @Operation(summary = "Apply a partial update to a SafetyInterlock")
    public SafetyInterlockResponse update(@PathVariable UUID id, @Valid @RequestBody SafetyInterlockUpdateRequest request) {
        return service.update(id, request);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    @Operation(summary = "Delete a SafetyInterlock")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
