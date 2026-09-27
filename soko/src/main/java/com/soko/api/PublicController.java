package com.soko.api;

import com.soko.domain.Customer;
import com.soko.domain.Tenant;
import com.soko.persistence.CustomerRepository;
import com.soko.persistence.OfferRepository;
import com.soko.persistence.TenantRepository;
import com.soko.platform.Errors;
import com.soko.routing.OrderService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * The two storefront views that need no account at all: a public product
 * catalogue, and guest checkout against it. Both are resolved by tenant slug
 * rather than by an authenticated principal's tenant, and both are
 * deliberately unauthenticated (see SecurityConfig).
 *
 * <p>Guest checkout exists because requiring an owner-provisioned account
 * before a first-time buyer can order at all is exactly the friction a real
 * storefront cannot afford. It reuses {@link OrderService#place}, the same
 * routing engine and the same conditional-update stock reservation a signed-in
 * customer's order goes through - a guest order is not a lesser order, it is
 * the same order with its customer record created on the spot instead of in
 * advance.
 */
@RestController
@RequestMapping("/v1/public")
public class PublicController {

    private final TenantRepository tenants;
    private final OfferRepository offers;
    private final CustomerRepository customers;
    private final OrderService orderService;

    public PublicController(
            TenantRepository tenants,
            OfferRepository offers,
            CustomerRepository customers,
            OrderService orderService) {
        this.tenants = tenants;
        this.offers = offers;
        this.customers = customers;
        this.orderService = orderService;
    }

    @GetMapping("/{slug}/products")
    public List<Map<String, Object>> catalogue(@PathVariable String slug) {
        Tenant tenant = tenant(slug);

        return offers.storefront(tenant.getId()).stream()
                .map(
                        r -> {
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

    public record CheckoutLine(@NotNull UUID productId, @Min(1) int quantity) {}

    public record Checkout(
            @NotBlank String customerName,
            @NotBlank String phone,
            @NotBlank String county,
            @NotEmpty List<CheckoutLine> lines) {}

    @PostMapping("/{slug}/orders")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> checkout(@PathVariable String slug, @Valid @RequestBody Checkout request) {
        Tenant tenant = tenant(slug);

        // Reuse the guest's existing customer record if this phone has ordered
        // from this tenant before, so a repeat guest buyer accumulates one
        // order history instead of a fresh customer row every visit.
        List<Customer> existing = customers.findByTenantIdAndPhone(tenant.getId(), request.phone());
        Customer customer;
        if (!existing.isEmpty()) {
            customer = existing.get(0);
        } else {
            Customer created = new Customer();
            created.setTenantId(tenant.getId());
            created.setName(request.customerName());
            created.setPhone(request.phone());
            created.setCounty(request.county());
            customer = customers.save(created);
        }

        List<OrderService.LineRequest> lines =
                request.lines().stream()
                        .map(l -> new OrderService.LineRequest(l.productId(), l.quantity()))
                        .toList();

        OrderService.Placed placed = orderService.place(tenant.getId(), customer.getId(), lines, true);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("orderId", placed.orderId());
        body.put("reference", placed.reference());
        body.put("totalCents", placed.revenueCents());
        body.put(
                "lines",
                placed.lines().stream()
                        .map(
                                l -> {
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

    private Tenant tenant(String slug) {
        return tenants.findBySlug(slug).orElseThrow(() -> new Errors.NotFound("no such storefront"));
    }
}
