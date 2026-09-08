package com.smartseason.fraud.web;

import com.smartseason.fraud.platform.PageResponse;
import com.smartseason.fraud.service.WorkerRiskScoreService;
import com.smartseason.fraud.web.dto.WorkerRiskScoreCreateRequest;
import com.smartseason.fraud.web.dto.WorkerRiskScoreResponse;
import com.smartseason.fraud.web.dto.WorkerRiskScoreUpdateRequest;
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
@RequestMapping("/api/fraud/v1/worker-risk-scores")
@Tag(name = "WorkerRiskScore", description = "Fraud rules engine, anomaly scoring, cases, evidence bundles, review queue")
public class WorkerRiskScoreController {

    private final WorkerRiskScoreService service;

    public WorkerRiskScoreController(WorkerRiskScoreService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'FARMER', 'MANAGER', 'FINANCE')")
    @Operation(summary = "List worker-risk-scores for the caller's tenant")
    public PageResponse<WorkerRiskScoreResponse> list(@PageableDefault(size = 20) Pageable pageable) {
        return service.list(pageable);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'FARMER', 'MANAGER', 'FINANCE')")
    @Operation(summary = "Fetch a single WorkerRiskScore by id")
    public WorkerRiskScoreResponse get(@PathVariable UUID id) {
        return service.get(id);
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN')")
    @Operation(summary = "Create a WorkerRiskScore")
    public ResponseEntity<WorkerRiskScoreResponse> create(@Valid @RequestBody WorkerRiskScoreCreateRequest request) {
        WorkerRiskScoreResponse created = service.create(request);
        return ResponseEntity.created(URI.create("/api/fraud/v1/worker-risk-scores/" + created.id())).body(created);
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN')")
    @Operation(summary = "Apply a partial update to a WorkerRiskScore")
    public WorkerRiskScoreResponse update(@PathVariable UUID id, @Valid @RequestBody WorkerRiskScoreUpdateRequest request) {
        return service.update(id, request);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN')")
    @Operation(summary = "Delete a WorkerRiskScore")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
