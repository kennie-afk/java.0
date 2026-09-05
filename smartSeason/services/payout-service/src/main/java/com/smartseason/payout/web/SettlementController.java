package com.smartseason.payout.web;

import com.smartseason.payout.platform.PageResponse;
import com.smartseason.payout.service.SettlementService;
import com.smartseason.payout.web.dto.SettlementCreateRequest;
import com.smartseason.payout.web.dto.SettlementResponse;
import com.smartseason.payout.web.dto.SettlementUpdateRequest;
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
@RequestMapping("/api/payout/v1/settlements")
@Tag(name = "Settlement", description = "Farmer settlements, bulk wage disbursement, fees, scheduling, fraud holds")
public class SettlementController {

    private final SettlementService service;

    public SettlementController(SettlementService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(summary = "List settlements for the caller's tenant")
    public PageResponse<SettlementResponse> list(@PageableDefault(size = 20) Pageable pageable) {
        return service.list(pageable);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Fetch a single Settlement by id")
    public SettlementResponse get(@PathVariable UUID id) {
        return service.get(id);
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'OPERATOR')")
    @Operation(summary = "Create a Settlement")
    public ResponseEntity<SettlementResponse> create(@Valid @RequestBody SettlementCreateRequest request) {
        SettlementResponse created = service.create(request);
        return ResponseEntity.created(URI.create("/api/payout/v1/settlements/" + created.id())).body(created);
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'OPERATOR')")
    @Operation(summary = "Apply a partial update to a Settlement")
    public SettlementResponse update(@PathVariable UUID id, @Valid @RequestBody SettlementUpdateRequest request) {
        return service.update(id, request);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    @Operation(summary = "Delete a Settlement")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
