package com.smartseason.traceability.web;

import com.smartseason.traceability.platform.PageResponse;
import com.smartseason.traceability.service.CertEvidenceService;
import com.smartseason.traceability.web.dto.CertEvidenceCreateRequest;
import com.smartseason.traceability.web.dto.CertEvidenceResponse;
import com.smartseason.traceability.web.dto.CertEvidenceUpdateRequest;
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
@RequestMapping("/api/traceability/v1/cert-evidence")
@Tag(name = "CertEvidence", description = "Farm-to-fork lineage graph, certifications, QR pass")
public class CertEvidenceController {

    private final CertEvidenceService service;

    public CertEvidenceController(CertEvidenceService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(summary = "List cert-evidence for the caller's tenant")
    public PageResponse<CertEvidenceResponse> list(@PageableDefault(size = 20) Pageable pageable) {
        return service.list(pageable);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Fetch a single CertEvidence by id")
    public CertEvidenceResponse get(@PathVariable UUID id) {
        return service.get(id);
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'OPERATOR')")
    @Operation(summary = "Create a CertEvidence")
    public ResponseEntity<CertEvidenceResponse> create(@Valid @RequestBody CertEvidenceCreateRequest request) {
        CertEvidenceResponse created = service.create(request);
        return ResponseEntity.created(URI.create("/api/traceability/v1/cert-evidence/" + created.id())).body(created);
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'OPERATOR')")
    @Operation(summary = "Apply a partial update to a CertEvidence")
    public CertEvidenceResponse update(@PathVariable UUID id, @Valid @RequestBody CertEvidenceUpdateRequest request) {
        return service.update(id, request);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    @Operation(summary = "Delete a CertEvidence")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
