package com.smartseason.order.web;

import com.smartseason.order.platform.PageResponse;
import com.smartseason.order.service.OrderLineService;
import com.smartseason.order.web.dto.OrderLineCreateRequest;
import com.smartseason.order.web.dto.OrderLineResponse;
import com.smartseason.order.web.dto.OrderLineUpdateRequest;
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
@RequestMapping("/api/order/v1/order-lines")
@Tag(name = "OrderLine", description = "Carts, orders, fulfillment saga, returns, disputes")
public class OrderLineController {

    private final OrderLineService service;

    public OrderLineController(OrderLineService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(summary = "List order-lines for the caller's tenant")
    public PageResponse<OrderLineResponse> list(@PageableDefault(size = 20) Pageable pageable) {
        return service.list(pageable);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Fetch a single OrderLine by id")
    public OrderLineResponse get(@PathVariable UUID id) {
        return service.get(id);
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'OPERATOR')")
    @Operation(summary = "Create a OrderLine")
    public ResponseEntity<OrderLineResponse> create(@Valid @RequestBody OrderLineCreateRequest request) {
        OrderLineResponse created = service.create(request);
        return ResponseEntity.created(URI.create("/api/order/v1/order-lines/" + created.id())).body(created);
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'OPERATOR')")
    @Operation(summary = "Apply a partial update to a OrderLine")
    public OrderLineResponse update(@PathVariable UUID id, @Valid @RequestBody OrderLineUpdateRequest request) {
        return service.update(id, request);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    @Operation(summary = "Delete a OrderLine")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
