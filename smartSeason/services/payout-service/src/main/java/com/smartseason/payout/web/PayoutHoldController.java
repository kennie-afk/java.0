package com.smartseason.payout.web;

import com.smartseason.payout.platform.PageResponse;
import com.smartseason.payout.service.PayoutHoldService;
import com.smartseason.payout.web.dto.PayoutHoldCreateRequest;
import com.smartseason.payout.web.dto.PayoutHoldResponse;
import com.smartseason.payout.web.dto.PayoutHoldUpdateRequest;
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
@RequestMapping("/api/payout/v1/payout-holds")
@Tag(name = "PayoutHold", description = "Farmer settlements, bulk wage disbursement, fees, scheduling, fraud holds")
public class PayoutHoldController {

    private final PayoutHoldService service;

    public PayoutHoldController(PayoutHoldService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'FARMER', 'FINANCE')")
    @Operation(summary = "List payout-holds for the caller's tenant")
    public PageResponse<PayoutHoldResponse> list(@PageableDefault(size = 20) Pageable pageable) {
        return service.list(pageable);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'FARMER', 'FINANCE')")
    @Operation(summary = "Fetch a single PayoutHold by id")
    public PayoutHoldResponse get(@PathVariable UUID id) {
        return service.get(id);
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'FINANCE')")
    @Operation(summary = "Create a PayoutHold")
    public ResponseEntity<PayoutHoldResponse> create(@Valid @RequestBody PayoutHoldCreateRequest request) {
        PayoutHoldResponse created = service.create(request);
        return ResponseEntity.created(URI.create("/api/payout/v1/payout-holds/" + created.id())).body(created);
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'FINANCE')")
    @Operation(summary = "Apply a partial update to a PayoutHold")
    public PayoutHoldResponse update(@PathVariable UUID id, @Valid @RequestBody PayoutHoldUpdateRequest request) {
        return service.update(id, request);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'FINANCE')")
    @Operation(summary = "Delete a PayoutHold")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
