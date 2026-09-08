package com.smartseason.payout.web;

import com.smartseason.payout.platform.PageResponse;
import com.smartseason.payout.service.PayoutItemService;
import com.smartseason.payout.web.dto.PayoutItemCreateRequest;
import com.smartseason.payout.web.dto.PayoutItemResponse;
import com.smartseason.payout.web.dto.PayoutItemUpdateRequest;
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
@RequestMapping("/api/payout/v1/payout-items")
@Tag(name = "PayoutItem", description = "Farmer settlements, bulk wage disbursement, fees, scheduling, fraud holds")
public class PayoutItemController {

    private final PayoutItemService service;

    public PayoutItemController(PayoutItemService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'FARMER', 'FINANCE')")
    @Operation(summary = "List payout-items for the caller's tenant")
    public PageResponse<PayoutItemResponse> list(@PageableDefault(size = 20) Pageable pageable) {
        return service.list(pageable);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'FARMER', 'FINANCE')")
    @Operation(summary = "Fetch a single PayoutItem by id")
    public PayoutItemResponse get(@PathVariable UUID id) {
        return service.get(id);
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'FINANCE')")
    @Operation(summary = "Create a PayoutItem")
    public ResponseEntity<PayoutItemResponse> create(@Valid @RequestBody PayoutItemCreateRequest request) {
        PayoutItemResponse created = service.create(request);
        return ResponseEntity.created(URI.create("/api/payout/v1/payout-items/" + created.id())).body(created);
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'FINANCE')")
    @Operation(summary = "Apply a partial update to a PayoutItem")
    public PayoutItemResponse update(@PathVariable UUID id, @Valid @RequestBody PayoutItemUpdateRequest request) {
        return service.update(id, request);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'FINANCE')")
    @Operation(summary = "Delete a PayoutItem")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
