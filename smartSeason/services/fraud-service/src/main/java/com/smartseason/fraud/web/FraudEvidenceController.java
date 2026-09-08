package com.smartseason.fraud.web;

import com.smartseason.fraud.platform.PageResponse;
import com.smartseason.fraud.service.FraudEvidenceService;
import com.smartseason.fraud.web.dto.FraudEvidenceCreateRequest;
import com.smartseason.fraud.web.dto.FraudEvidenceResponse;
import com.smartseason.fraud.web.dto.FraudEvidenceUpdateRequest;
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
@RequestMapping("/api/fraud/v1/fraud-evidence")
@Tag(name = "FraudEvidence", description = "Fraud rules engine, anomaly scoring, cases, evidence bundles, review queue")
public class FraudEvidenceController {

    private final FraudEvidenceService service;

    public FraudEvidenceController(FraudEvidenceService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'FARMER', 'MANAGER', 'FINANCE')")
    @Operation(summary = "List fraud-evidence for the caller's tenant")
    public PageResponse<FraudEvidenceResponse> list(@PageableDefault(size = 20) Pageable pageable) {
        return service.list(pageable);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'FARMER', 'MANAGER', 'FINANCE')")
    @Operation(summary = "Fetch a single FraudEvidence by id")
    public FraudEvidenceResponse get(@PathVariable UUID id) {
        return service.get(id);
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN')")
    @Operation(summary = "Create a FraudEvidence")
    public ResponseEntity<FraudEvidenceResponse> create(@Valid @RequestBody FraudEvidenceCreateRequest request) {
        FraudEvidenceResponse created = service.create(request);
        return ResponseEntity.created(URI.create("/api/fraud/v1/fraud-evidence/" + created.id())).body(created);
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN')")
    @Operation(summary = "Apply a partial update to a FraudEvidence")
    public FraudEvidenceResponse update(@PathVariable UUID id, @Valid @RequestBody FraudEvidenceUpdateRequest request) {
        return service.update(id, request);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN')")
    @Operation(summary = "Delete a FraudEvidence")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
