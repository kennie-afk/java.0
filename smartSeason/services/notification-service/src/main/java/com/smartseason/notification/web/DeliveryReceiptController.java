package com.smartseason.notification.web;

import com.smartseason.notification.platform.PageResponse;
import com.smartseason.notification.service.DeliveryReceiptService;
import com.smartseason.notification.web.dto.DeliveryReceiptCreateRequest;
import com.smartseason.notification.web.dto.DeliveryReceiptResponse;
import com.smartseason.notification.web.dto.DeliveryReceiptUpdateRequest;
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
@RequestMapping("/api/notification/v1/delivery-receipts")
@Tag(name = "DeliveryReceipt", description = "SMS, USSD, push, WhatsApp, email; EN/SW templating, delivery tracking, quiet hours")
public class DeliveryReceiptController {

    private final DeliveryReceiptService service;

    public DeliveryReceiptController(DeliveryReceiptService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'FARMER')")
    @Operation(summary = "List delivery-receipts for the caller's tenant")
    public PageResponse<DeliveryReceiptResponse> list(@PageableDefault(size = 20) Pageable pageable) {
        return service.list(pageable);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'FARMER')")
    @Operation(summary = "Fetch a single DeliveryReceipt by id")
    public DeliveryReceiptResponse get(@PathVariable UUID id) {
        return service.get(id);
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN')")
    @Operation(summary = "Create a DeliveryReceipt")
    public ResponseEntity<DeliveryReceiptResponse> create(@Valid @RequestBody DeliveryReceiptCreateRequest request) {
        DeliveryReceiptResponse created = service.create(request);
        return ResponseEntity.created(URI.create("/api/notification/v1/delivery-receipts/" + created.id())).body(created);
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN')")
    @Operation(summary = "Apply a partial update to a DeliveryReceipt")
    public DeliveryReceiptResponse update(@PathVariable UUID id, @Valid @RequestBody DeliveryReceiptUpdateRequest request) {
        return service.update(id, request);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN')")
    @Operation(summary = "Delete a DeliveryReceipt")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
