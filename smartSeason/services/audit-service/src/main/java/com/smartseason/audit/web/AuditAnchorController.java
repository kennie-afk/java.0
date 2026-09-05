package com.smartseason.audit.web;

import com.smartseason.audit.platform.PageResponse;
import com.smartseason.audit.service.AuditAnchorService;
import com.smartseason.audit.web.dto.AuditAnchorCreateRequest;
import com.smartseason.audit.web.dto.AuditAnchorResponse;
import com.smartseason.audit.web.dto.AuditAnchorUpdateRequest;
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
@RequestMapping("/api/audit/v1/audit-anchors")
@Tag(name = "AuditAnchor", description = "Tamper-evident hash-chained audit log of privileged actions across all services")
public class AuditAnchorController {

    private final AuditAnchorService service;

    public AuditAnchorController(AuditAnchorService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(summary = "List audit-anchors for the caller's tenant")
    public PageResponse<AuditAnchorResponse> list(@PageableDefault(size = 20) Pageable pageable) {
        return service.list(pageable);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Fetch a single AuditAnchor by id")
    public AuditAnchorResponse get(@PathVariable UUID id) {
        return service.get(id);
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'OPERATOR')")
    @Operation(summary = "Create a AuditAnchor")
    public ResponseEntity<AuditAnchorResponse> create(@Valid @RequestBody AuditAnchorCreateRequest request) {
        AuditAnchorResponse created = service.create(request);
        return ResponseEntity.created(URI.create("/api/audit/v1/audit-anchors/" + created.id())).body(created);
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'OPERATOR')")
    @Operation(summary = "Apply a partial update to a AuditAnchor")
    public AuditAnchorResponse update(@PathVariable UUID id, @Valid @RequestBody AuditAnchorUpdateRequest request) {
        return service.update(id, request);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    @Operation(summary = "Delete a AuditAnchor")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
