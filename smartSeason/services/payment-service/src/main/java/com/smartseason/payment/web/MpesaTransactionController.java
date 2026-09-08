package com.smartseason.payment.web;

import com.smartseason.payment.platform.PageResponse;
import com.smartseason.payment.service.MpesaTransactionService;
import com.smartseason.payment.web.dto.MpesaTransactionCreateRequest;
import com.smartseason.payment.web.dto.MpesaTransactionResponse;
import com.smartseason.payment.web.dto.MpesaTransactionUpdateRequest;
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
@RequestMapping("/api/payment/v1/mpesa-transactions")
@Tag(name = "MpesaTransaction", description = "Payment intents, M-Pesa STK/C2B/B2C, cards, wallets, escrow, callback reconciliation")
public class MpesaTransactionController {

    private final MpesaTransactionService service;

    public MpesaTransactionController(MpesaTransactionService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'FARMER', 'FINANCE', 'BUYER')")
    @Operation(summary = "List mpesa-transactions for the caller's tenant")
    public PageResponse<MpesaTransactionResponse> list(@PageableDefault(size = 20) Pageable pageable) {
        return service.list(pageable);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'FARMER', 'FINANCE', 'BUYER')")
    @Operation(summary = "Fetch a single MpesaTransaction by id")
    public MpesaTransactionResponse get(@PathVariable UUID id) {
        return service.get(id);
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'FINANCE')")
    @Operation(summary = "Create a MpesaTransaction")
    public ResponseEntity<MpesaTransactionResponse> create(@Valid @RequestBody MpesaTransactionCreateRequest request) {
        MpesaTransactionResponse created = service.create(request);
        return ResponseEntity.created(URI.create("/api/payment/v1/mpesa-transactions/" + created.id())).body(created);
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'FINANCE')")
    @Operation(summary = "Apply a partial update to a MpesaTransaction")
    public MpesaTransactionResponse update(@PathVariable UUID id, @Valid @RequestBody MpesaTransactionUpdateRequest request) {
        return service.update(id, request);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'FINANCE')")
    @Operation(summary = "Delete a MpesaTransaction")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
