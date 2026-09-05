package com.smartseason.payment.web;

import com.smartseason.payment.platform.PageResponse;
import com.smartseason.payment.service.ProviderCallbackService;
import com.smartseason.payment.web.dto.ProviderCallbackCreateRequest;
import com.smartseason.payment.web.dto.ProviderCallbackResponse;
import com.smartseason.payment.web.dto.ProviderCallbackUpdateRequest;
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
@RequestMapping("/api/payment/v1/provider-callbacks")
@Tag(name = "ProviderCallback", description = "Payment intents, M-Pesa STK/C2B/B2C, cards, wallets, escrow, callback reconciliation")
public class ProviderCallbackController {

    private final ProviderCallbackService service;

    public ProviderCallbackController(ProviderCallbackService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(summary = "List provider-callbacks for the caller's tenant")
    public PageResponse<ProviderCallbackResponse> list(@PageableDefault(size = 20) Pageable pageable) {
        return service.list(pageable);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Fetch a single ProviderCallback by id")
    public ProviderCallbackResponse get(@PathVariable UUID id) {
        return service.get(id);
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'OPERATOR')")
    @Operation(summary = "Create a ProviderCallback")
    public ResponseEntity<ProviderCallbackResponse> create(@Valid @RequestBody ProviderCallbackCreateRequest request) {
        ProviderCallbackResponse created = service.create(request);
        return ResponseEntity.created(URI.create("/api/payment/v1/provider-callbacks/" + created.id())).body(created);
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'OPERATOR')")
    @Operation(summary = "Apply a partial update to a ProviderCallback")
    public ProviderCallbackResponse update(@PathVariable UUID id, @Valid @RequestBody ProviderCallbackUpdateRequest request) {
        return service.update(id, request);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    @Operation(summary = "Delete a ProviderCallback")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
