package com.smartseason.fraud.web;

import com.smartseason.fraud.platform.PageResponse;
import com.smartseason.fraud.service.FraudRuleService;
import com.smartseason.fraud.web.dto.FraudRuleCreateRequest;
import com.smartseason.fraud.web.dto.FraudRuleResponse;
import com.smartseason.fraud.web.dto.FraudRuleUpdateRequest;
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
@RequestMapping("/api/fraud/v1/fraud-rules")
@Tag(name = "FraudRule", description = "Fraud rules engine, anomaly scoring, cases, evidence bundles, review queue")
public class FraudRuleController {

    private final FraudRuleService service;

    public FraudRuleController(FraudRuleService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(summary = "List fraud-rules for the caller's tenant")
    public PageResponse<FraudRuleResponse> list(@PageableDefault(size = 20) Pageable pageable) {
        return service.list(pageable);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Fetch a single FraudRule by id")
    public FraudRuleResponse get(@PathVariable UUID id) {
        return service.get(id);
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'OPERATOR')")
    @Operation(summary = "Create a FraudRule")
    public ResponseEntity<FraudRuleResponse> create(@Valid @RequestBody FraudRuleCreateRequest request) {
        FraudRuleResponse created = service.create(request);
        return ResponseEntity.created(URI.create("/api/fraud/v1/fraud-rules/" + created.id())).body(created);
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'OPERATOR')")
    @Operation(summary = "Apply a partial update to a FraudRule")
    public FraudRuleResponse update(@PathVariable UUID id, @Valid @RequestBody FraudRuleUpdateRequest request) {
        return service.update(id, request);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    @Operation(summary = "Delete a FraudRule")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
