package com.soko.api;

import com.soko.domain.SalesOrder;
import com.soko.persistence.*;
import com.soko.platform.Errors;
import com.soko.routing.OrderService;
import com.soko.security.Principal;
import com.soko.security.TenantContext;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.*;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/v1/shop")
public class ShopController {

    private final OfferRepository offers;
    private final OrderRepository orders;
    private final OrderLineRepository orderLines;
    private final ProductRepository products;
    private final CustomerRepository customers;
    private final OrderService orderService;
    private final TenantContext context;

    public ShopController(
            OfferRepository offers,
            OrderRepository orders,
            OrderLineRepository orderLines,
            ProductRepository products,
            CustomerRepository customers,
            OrderService orderService,
            TenantContext context) {
        this.offers = offers;
        this.orders = orders;
        this.orderLines = orderLines;
        this.products = products;
        this.customers = customers;
        this.orderService = orderService;
        this.context = context;
    }

    private Principal shopper() {
        Principal principal = context.current();
        if (principal.customerId() == null) {
            throw new Errors.Unauthorized("this endpoint is for customer accounts");
        }
        return principal;
    }

    @GetMapping("/products")
    public List<Map<String, Object>> catalogue() {
        Principal principal = shopper();
        return offers.storefront(principal.tenantId()).stream()
                .map(r -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("id", r[0]);
                    row.put("sku", r[1]);
                    row.put("name", r[2]);
                    row.put("category", r[3]);
                    row.put("unit", r[4]);
                    row.put("perishable", r[5]);
                    row.put("chilled", r[6]);
                    row.put("shelfLifeHours", r[7]);
                    row.put("priceCents", r[8]);
                    row.put("inStock", ((Number) r[9]).longValue());
                    return row;
                })
                .filter(row -> ((Number) row.get("inStock")).longValue() > 0)
                .toList();
    }

    public record BasketLine(@NotNull UUID productId, @Min(1) int quantity) {}

    public record Checkout(@NotEmpty List<BasketLine> lines) {}

    @PostMapping("/orders")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> checkout(@Valid @RequestBody Checkout request) {
        Principal principal = shopper();
        List<OrderService.LineRequest> lines =
                request.lines().stream()
                        .map(l -> new OrderService.LineRequest(l.productId(), l.quantity()))
                        .toList();

        OrderService.Placed placed =
                orderService.place(principal.tenantId(), principal.customerId(), lines, true);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("orderId", placed.orderId());
        body.put("reference", placed.reference());
        body.put("totalCents", placed.revenueCents());
        body.put(
                "lines",
                placed.lines().stream()
                        .map(l -> {
                            Map<String, Object> row = new LinkedHashMap<>();
                            row.put("product", l.productName());
                            row.put("quantity", l.quantity());
                            row.put("unitPriceCents", l.unitPriceCents());
                            row.put("lineTotalCents", l.unitPriceCents() * l.quantity());
                            return row;
                        })
                        .toList());
        return body;
    }

    @GetMapping("/orders")
    public List<Map<String, Object>> myOrders() {
        Principal principal = shopper();
        return orders
                .findByCustomerIdOrderByPlacedAtDesc(principal.customerId())
                .stream()
                .map(order -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("id", order.getId());
                    row.put("reference", order.getReference());
                    row.put("status", order.getStatus());
                    row.put("totalCents", order.getRevenueCents());
                    row.put("placedAt", order.getPlacedAt());
                    return row;
                })
                .toList();
    }

    @GetMapping("/orders/{id}")
    public Map<String, Object> myOrder(@PathVariable UUID id) {
        Principal principal = shopper();
        SalesOrder order =
                orders.findByIdAndCustomerId(id, principal.customerId())
                        .orElseThrow(() -> new Errors.NotFound("no such order"));

        Map<UUID, String> names =
                products.findByTenantIdOrderByNameAsc(principal.tenantId()).stream()
                        .collect(Collectors.toMap(p -> p.getId(), p -> p.getName()));

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("reference", order.getReference());
        body.put("status", order.getStatus());
        body.put("totalCents", order.getRevenueCents());
        body.put("placedAt", order.getPlacedAt());
        body.put(
                "lines",
                orderLines.findByOrderId(order.getId()).stream()
                        .map(line -> {
                            Map<String, Object> row = new LinkedHashMap<>();
                            row.put("product", names.get(line.getProductId()));
                            row.put("quantity", line.getQuantity());
                            row.put("unitPriceCents", line.getUnitPriceCents());
                            row.put("lineTotalCents",
                                    line.getUnitPriceCents() * line.getQuantity());
                            row.put("status", line.getStatus());
                            row.put("dispatchedAt", line.getDispatchedAt());
                            row.put("deliveredAt", line.getDeliveredAt());
                            return row;
                        })
                        .toList());
        return body;
    }
}
