package com.smartseason.order.web;

import com.smartseason.order.platform.PageResponse;
import com.smartseason.order.service.OrderSagaStateService;
import com.smartseason.order.web.dto.OrderSagaStateCreateRequest;
import com.smartseason.order.web.dto.OrderSagaStateResponse;
import com.smartseason.order.web.dto.OrderSagaStateUpdateRequest;
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
@RequestMapping("/api/order/v1/order-saga-states")
@Tag(name = "OrderSagaState", description = "Carts, orders, fulfillment saga, returns, disputes")
public class OrderSagaStateController {

    private final OrderSagaStateService service;

    public OrderSagaStateController(OrderSagaStateService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'FARMER', 'STOREKEEPER', 'FINANCE', 'BUYER')")
    @Operation(summary = "List order-saga-states for the caller's tenant")
    public PageResponse<OrderSagaStateResponse> list(@PageableDefault(size = 20) Pageable pageable) {
        return service.list(pageable);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'FARMER', 'STOREKEEPER', 'FINANCE', 'BUYER')")
    @Operation(summary = "Fetch a single OrderSagaState by id")
    public OrderSagaStateResponse get(@PathVariable UUID id) {
        return service.get(id);
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'FARMER', 'BUYER')")
    @Operation(summary = "Create a OrderSagaState")
    public ResponseEntity<OrderSagaStateResponse> create(@Valid @RequestBody OrderSagaStateCreateRequest request) {
        OrderSagaStateResponse created = service.create(request);
        return ResponseEntity.created(URI.create("/api/order/v1/order-saga-states/" + created.id())).body(created);
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'FARMER', 'BUYER')")
    @Operation(summary = "Apply a partial update to a OrderSagaState")
    public OrderSagaStateResponse update(@PathVariable UUID id, @Valid @RequestBody OrderSagaStateUpdateRequest request) {
        return service.update(id, request);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'BUYER')")
    @Operation(summary = "Delete a OrderSagaState")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
