package com.smartseason.order.web;

import com.smartseason.order.platform.PageResponse;
import com.smartseason.order.service.OrderReturnService;
import com.smartseason.order.web.dto.OrderReturnCreateRequest;
import com.smartseason.order.web.dto.OrderReturnResponse;
import com.smartseason.order.web.dto.OrderReturnUpdateRequest;
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
@RequestMapping("/api/order/v1/order-returns")
@Tag(name = "OrderReturn", description = "Carts, orders, fulfillment saga, returns, disputes")
public class OrderReturnController {

    private final OrderReturnService service;

    public OrderReturnController(OrderReturnService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'FARMER', 'STOREKEEPER', 'FINANCE', 'BUYER')")
    @Operation(summary = "List order-returns for the caller's tenant")
    public PageResponse<OrderReturnResponse> list(@PageableDefault(size = 20) Pageable pageable) {
        return service.list(pageable);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'FARMER', 'STOREKEEPER', 'FINANCE', 'BUYER')")
    @Operation(summary = "Fetch a single OrderReturn by id")
    public OrderReturnResponse get(@PathVariable UUID id) {
        return service.get(id);
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'FARMER', 'BUYER')")
    @Operation(summary = "Create a OrderReturn")
    public ResponseEntity<OrderReturnResponse> create(@Valid @RequestBody OrderReturnCreateRequest request) {
        OrderReturnResponse created = service.create(request);
        return ResponseEntity.created(URI.create("/api/order/v1/order-returns/" + created.id())).body(created);
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'FARMER', 'BUYER')")
    @Operation(summary = "Apply a partial update to a OrderReturn")
    public OrderReturnResponse update(@PathVariable UUID id, @Valid @RequestBody OrderReturnUpdateRequest request) {
        return service.update(id, request);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'BUYER')")
    @Operation(summary = "Delete a OrderReturn")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
