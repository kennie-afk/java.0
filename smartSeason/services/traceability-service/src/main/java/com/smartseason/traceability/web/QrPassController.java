package com.smartseason.traceability.web;

import com.smartseason.traceability.platform.PageResponse;
import com.smartseason.traceability.service.QrPassService;
import com.smartseason.traceability.web.dto.QrPassCreateRequest;
import com.smartseason.traceability.web.dto.QrPassResponse;
import com.smartseason.traceability.web.dto.QrPassUpdateRequest;
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
@RequestMapping("/api/traceability/v1/qr-passes")
@Tag(name = "QrPass", description = "Farm-to-fork lineage graph, certifications, QR pass")
public class QrPassController {

    private final QrPassService service;

    public QrPassController(QrPassService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(summary = "List qr-passes for the caller's tenant")
    public PageResponse<QrPassResponse> list(@PageableDefault(size = 20) Pageable pageable) {
        return service.list(pageable);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Fetch a single QrPass by id")
    public QrPassResponse get(@PathVariable UUID id) {
        return service.get(id);
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'OPERATOR')")
    @Operation(summary = "Create a QrPass")
    public ResponseEntity<QrPassResponse> create(@Valid @RequestBody QrPassCreateRequest request) {
        QrPassResponse created = service.create(request);
        return ResponseEntity.created(URI.create("/api/traceability/v1/qr-passes/" + created.id())).body(created);
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'OPERATOR')")
    @Operation(summary = "Apply a partial update to a QrPass")
    public QrPassResponse update(@PathVariable UUID id, @Valid @RequestBody QrPassUpdateRequest request) {
        return service.update(id, request);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    @Operation(summary = "Delete a QrPass")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
