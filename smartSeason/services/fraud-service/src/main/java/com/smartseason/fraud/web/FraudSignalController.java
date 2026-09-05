package com.smartseason.fraud.web;

import com.smartseason.fraud.platform.PageResponse;
import com.smartseason.fraud.service.FraudSignalService;
import com.smartseason.fraud.web.dto.FraudSignalCreateRequest;
import com.smartseason.fraud.web.dto.FraudSignalResponse;
import com.smartseason.fraud.web.dto.FraudSignalUpdateRequest;
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
@RequestMapping("/api/fraud/v1/fraud-signals")
@Tag(name = "FraudSignal", description = "Fraud rules engine, anomaly scoring, cases, evidence bundles, review queue")
public class FraudSignalController {

    private final FraudSignalService service;

    public FraudSignalController(FraudSignalService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(summary = "List fraud-signals for the caller's tenant")
    public PageResponse<FraudSignalResponse> list(@PageableDefault(size = 20) Pageable pageable) {
        return service.list(pageable);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Fetch a single FraudSignal by id")
    public FraudSignalResponse get(@PathVariable UUID id) {
        return service.get(id);
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'OPERATOR')")
    @Operation(summary = "Create a FraudSignal")
    public ResponseEntity<FraudSignalResponse> create(@Valid @RequestBody FraudSignalCreateRequest request) {
        FraudSignalResponse created = service.create(request);
        return ResponseEntity.created(URI.create("/api/fraud/v1/fraud-signals/" + created.id())).body(created);
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'OPERATOR')")
    @Operation(summary = "Apply a partial update to a FraudSignal")
    public FraudSignalResponse update(@PathVariable UUID id, @Valid @RequestBody FraudSignalUpdateRequest request) {
        return service.update(id, request);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    @Operation(summary = "Delete a FraudSignal")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
