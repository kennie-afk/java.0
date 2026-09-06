package com.soko.api;

import com.soko.domain.*;
import com.soko.persistence.*;
import com.soko.platform.Errors;
import com.soko.routing.OrderService;
import com.soko.security.Principal;
import com.soko.security.TenantContext;
import com.soko.security.Tokens;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.*;
import java.util.stream.Collectors;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/v1")
public class ApiController {

    private static final org.slf4j.Logger resetLog =
            org.slf4j.LoggerFactory.getLogger("com.soko.auth.reset");

    private final TenantRepository tenants;
    private final UserRepository users;
    private final SupplierRepository suppliers;
    private final ProductRepository products;
    private final OfferRepository offers;
    private final CustomerRepository customers;
    private final OrderRepository orders;
    private final OrderLineRepository orderLines;
    private final OrderService orderService;
    private final PasswordEncoder encoder;
    private final Tokens tokens;
    private final TenantContext context;

    public ApiController(
            TenantRepository tenants, UserRepository users, SupplierRepository suppliers,
            ProductRepository products, OfferRepository offers, CustomerRepository customers,
            OrderRepository orders, OrderLineRepository orderLines, OrderService orderService,
            PasswordEncoder encoder, Tokens tokens, TenantContext context) {
        this.tenants = tenants; this.users = users; this.suppliers = suppliers;
        this.products = products; this.offers = offers; this.customers = customers;
        this.orders = orders; this.orderLines = orderLines; this.orderService = orderService;
        this.encoder = encoder; this.tokens = tokens; this.context = context;
    }

    public record RegisterRequest(
            @NotBlank String organisationName, @NotBlank String fullName,
            @Email @NotBlank String email, @Size(min = 10) String password) {}

    public record LoginRequest(@Email @NotBlank String email, @NotBlank String password) {}

    public record Session(String accessToken, String tenantId, String fullName, String role,
            String organisation, long expiresInSeconds) {}

    public record TeamInvite(
            @NotBlank String fullName, @Email @NotBlank String email,
            @Size(min = 10) String password, @NotBlank String role,
            UUID supplierId, UUID customerId) {}

    public record ForgotRequest(@Email @NotBlank String email) {}

    public record Acknowledged(String detail) {}

    @PostMapping("/auth/register")
    @ResponseStatus(HttpStatus.CREATED)
    public Session register(@Valid @RequestBody RegisterRequest request) {
        if (users.findByEmailAndStatus(request.email(), "ACTIVE").isPresent()) {
            throw new Errors.BadRequest("that email is already registered");
        }
        Tenant tenant = new Tenant();
        tenant.setName(request.organisationName());
        tenant.setSlug(slug(request.organisationName()));
        tenant = tenants.save(tenant);

        AppUser user = new AppUser();
        user.setTenantId(tenant.getId());
        user.setEmail(request.email());
        user.setFullName(request.fullName());
        user.setPasswordHash(encoder.encode(request.password()));
        user.setRole("OWNER");
        user = users.save(user);

        return session(user);
    }

    @PostMapping("/auth/login")
    public Session login(@Valid @RequestBody LoginRequest request) {
        AppUser user = users.findByEmailAndStatus(request.email(), "ACTIVE")
                .orElseThrow(() -> new Errors.Unauthorized("those credentials are not valid"));
        if (!encoder.matches(request.password(), user.getPasswordHash())) {
            throw new Errors.Unauthorized("those credentials are not valid");
        }
        return session(user);
    }

    private Session session(AppUser user) {
        Principal principal =
                new Principal(
                        user.getId(), user.getTenantId(), user.getEmail(), user.getRole(),
                        user.getSupplierId(), user.getCustomerId());
        String organisation = tenants.findById(user.getTenantId()).map(Tenant::getName).orElse("");
        return new Session(tokens.issue(principal), user.getTenantId().toString(),
                user.getFullName(), user.getRole(), organisation, tokens.ttlSeconds());
    }

    @PostMapping("/users")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> inviteUser(@Valid @RequestBody TeamInvite request) {
        UUID tenantId = context.current().tenantId();
        if (!"OWNER".equals(context.current().role())) {
            throw new Errors.Unauthorized("only an owner can add accounts");
        }
        if (users.findByEmailAndStatus(request.email(), "ACTIVE").isPresent()) {
            throw new Errors.BadRequest("that email is already registered");
        }
        String role = request.role().toUpperCase();
        if (!List.of("OPERATOR", "SUPPLIER", "CUSTOMER").contains(role)) {
            throw new Errors.BadRequest("role must be OPERATOR, SUPPLIER or CUSTOMER");
        }
        if ("SUPPLIER".equals(role)) {
            suppliers.findByIdAndTenantId(request.supplierId(), tenantId)
                    .orElseThrow(() -> new Errors.BadRequest("a supplier account needs a supplier"));
        }
        if ("CUSTOMER".equals(role)) {
            customers.findByIdAndTenantId(request.customerId(), tenantId)
                    .orElseThrow(() -> new Errors.BadRequest("a customer account needs a customer"));
        }

        AppUser user = new AppUser();
        user.setTenantId(tenantId);
        user.setEmail(request.email());
        user.setFullName(request.fullName());
        user.setPasswordHash(encoder.encode(request.password()));
        user.setRole(role);
        user.setSupplierId("SUPPLIER".equals(role) ? request.supplierId() : null);
        user.setCustomerId("CUSTOMER".equals(role) ? request.customerId() : null);
        user = users.save(user);

        return Map.of("id", user.getId(), "email", user.getEmail(), "role", user.getRole());
    }

    @PostMapping("/auth/forgot")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public Acknowledged forgot(@Valid @RequestBody ForgotRequest request) {
        users.findByEmailAndStatus(request.email(), "ACTIVE")
                .ifPresent(user -> resetLog.info(
                        "password reset requested for user {} on tenant {}",
                        user.getId(), user.getTenantId()));
        return new Acknowledged(
                "If that email has an account, a reset link is on its way.");
    }

    public record SupplierRequest(
            @NotBlank String name, @NotBlank String county,
            @Min(1) int leadTimeHours, boolean coldChain, Double reliability) {}

    @GetMapping("/suppliers")
    public List<Supplier> listSuppliers() {
        return suppliers.findByTenantIdOrderByNameAsc(context.current().tenantId());
    }

    @PostMapping("/suppliers")
    @ResponseStatus(HttpStatus.CREATED)
    public Supplier createSupplier(@Valid @RequestBody SupplierRequest request) {
        Supplier supplier = new Supplier();
        supplier.setTenantId(context.current().tenantId());
        supplier.setName(request.name());
        supplier.setCounty(request.county());
        supplier.setLeadTimeHours(request.leadTimeHours());
        supplier.setColdChain(request.coldChain());
        if (request.reliability() != null) {
            supplier.setReliability(java.math.BigDecimal.valueOf(request.reliability()));
        }
        return suppliers.save(supplier);
    }

    public record ProductRequest(
            @NotBlank String sku, @NotBlank String name, @NotBlank String category,
            @NotBlank String unit, boolean perishable, boolean requiresColdChain,
            @Min(1) int shelfLifeHours, @Min(1) long listPriceCents) {}

    @GetMapping("/products")
    public List<Product> listProducts() {
        return products.findByTenantIdOrderByNameAsc(context.current().tenantId());
    }

    @PostMapping("/products")
    @ResponseStatus(HttpStatus.CREATED)
    public Product createProduct(@Valid @RequestBody ProductRequest request) {
        Product product = new Product();
        product.setTenantId(context.current().tenantId());
        product.setSku(request.sku());
        product.setName(request.name());
        product.setCategory(request.category());
        product.setUnit(request.unit());
        product.setPerishable(request.perishable());
        product.setRequiresColdChain(request.requiresColdChain());
        product.setShelfLifeHours(request.shelfLifeHours());
        product.setListPriceCents(request.listPriceCents());
        return products.save(product);
    }

    public record OfferRequest(
            @NotNull UUID supplierId, @NotNull UUID productId,
            @Min(1) long costCents, @Min(0) int availableQty) {}

    @GetMapping("/offers")
    public List<Map<String, Object>> listOffers() {
        UUID tenantId = context.current().tenantId();
        Map<UUID, Supplier> supplierIndex = suppliers.findByTenantIdOrderByNameAsc(tenantId)
                .stream().collect(Collectors.toMap(Supplier::getId, s -> s));
        Map<UUID, Product> productIndex = products.findByTenantIdOrderByNameAsc(tenantId)
                .stream().collect(Collectors.toMap(Product::getId, p -> p));

        return offers.findByTenantIdOrderByCostCentsAsc(tenantId).stream()
                .map(o -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    Supplier s = supplierIndex.get(o.getSupplierId());
                    Product p = productIndex.get(o.getProductId());
                    row.put("id", o.getId());
                    row.put("supplier", s == null ? null : s.getName());
                    row.put("coldChain", s != null && s.isColdChain());
                    row.put("leadTimeHours", s == null ? null : s.getLeadTimeHours());
                    row.put("product", p == null ? null : p.getName());
                    row.put("costCents", o.getCostCents());
                    row.put("listPriceCents", p == null ? null : p.getListPriceCents());
                    row.put("availableQty", o.getAvailableQty());
                    row.put("status", o.getStatus());
                    return row;
                })
                .toList();
    }

    @PostMapping("/offers")
    @ResponseStatus(HttpStatus.CREATED)
    public Offer createOffer(@Valid @RequestBody OfferRequest request) {
        UUID tenantId = context.current().tenantId();
        suppliers.findByIdAndTenantId(request.supplierId(), tenantId)
                .orElseThrow(() -> new Errors.NotFound("no such supplier"));
        products.findByIdAndTenantId(request.productId(), tenantId)
                .orElseThrow(() -> new Errors.NotFound("no such product"));
        Offer offer = new Offer();
        offer.setTenantId(tenantId);
        offer.setSupplierId(request.supplierId());
        offer.setProductId(request.productId());
        offer.setCostCents(request.costCents());
        offer.setAvailableQty(request.availableQty());
        return offers.save(offer);
    }

    public record CustomerRequest(
            @NotBlank String name, @NotBlank String phone, @NotBlank String county) {}

    @GetMapping("/customers")
    public List<Customer> listCustomers() {
        return customers.findByTenantIdOrderByNameAsc(context.current().tenantId());
    }

    @PostMapping("/customers")
    @ResponseStatus(HttpStatus.CREATED)
    public Customer createCustomer(@Valid @RequestBody CustomerRequest request) {
        Customer customer = new Customer();
        customer.setTenantId(context.current().tenantId());
        customer.setName(request.name());
        customer.setPhone(request.phone());
        customer.setCounty(request.county());
        return customers.save(customer);
    }

    public record OrderLineRequest(@NotNull UUID productId, @Min(1) int quantity) {}

    public record OrderRequest(
            @NotNull UUID customerId, @NotEmpty List<OrderLineRequest> lines) {}

    @PostMapping("/orders")
    @ResponseStatus(HttpStatus.CREATED)
    public OrderService.Placed placeOrder(@Valid @RequestBody OrderRequest request) {
        List<OrderService.LineRequest> lines = request.lines().stream()
                .map(l -> new OrderService.LineRequest(l.productId(), l.quantity()))
                .toList();
        return orderService.place(context.current().tenantId(), request.customerId(), lines);
    }

    @GetMapping("/orders")
    public List<Map<String, Object>> listOrders(@RequestParam(defaultValue = "50") int limit) {
        UUID tenantId = context.current().tenantId();
        return orders.listWithCustomer(tenantId, Math.min(limit, 200)).stream()
                .map(r -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("id", r[0]);
                    row.put("reference", r[1]);
                    row.put("customer", r[2]);
                    row.put("county", r[3]);
                    row.put("status", r[4]);
                    row.put("revenueCents", r[5]);
                    row.put("costCents", r[6]);
                    row.put("marginCents", r[7]);
                    row.put("placedAt", r[8]);
                    return row;
                })
                .toList();
    }

    @GetMapping("/orders/{id}")
    public Map<String, Object> getOrder(@PathVariable UUID id) {
        UUID tenantId = context.current().tenantId();
        SalesOrder order = orders.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new Errors.NotFound("no such order"));
        Map<UUID, Product> productIndex = products.findByTenantIdOrderByNameAsc(tenantId)
                .stream().collect(Collectors.toMap(Product::getId, p -> p));
        Map<UUID, Supplier> supplierIndex = suppliers.findByTenantIdOrderByNameAsc(tenantId)
                .stream().collect(Collectors.toMap(Supplier::getId, s -> s));

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("id", order.getId());
        body.put("reference", order.getReference());
        body.put("status", order.getStatus());
        body.put("revenueCents", order.getRevenueCents());
        body.put("costCents", order.getCostCents());
        body.put("marginCents", order.getMarginCents());
        body.put("lines", orderLines.findByOrderId(order.getId()).stream().map(l -> {
            Map<String, Object> row = new LinkedHashMap<>();
            Product p = productIndex.get(l.getProductId());
            Supplier s = supplierIndex.get(l.getSupplierId());
            row.put("product", p == null ? null : p.getName());
            row.put("supplier", s == null ? null : s.getName());
            row.put("quantity", l.getQuantity());
            row.put("unitPriceCents", l.getUnitPriceCents());
            row.put("unitCostCents", l.getUnitCostCents());
            row.put("marginCents", (l.getUnitPriceCents() - l.getUnitCostCents()) * l.getQuantity());
            row.put("routingReason", l.getRoutingReason());
            return row;
        }).toList());
        return body;
    }

    @GetMapping("/overview")
    public Map<String, Object> overview() {
        Object[] raw = orders.summarise(context.current().tenantId());
        Object[] r = (raw.length == 1 && raw[0] instanceof Object[] inner) ? inner : raw;

        long orderCount = ((Number) r[0]).longValue();
        long revenue = ((Number) r[1]).longValue();
        long margin = ((Number) r[2]).longValue();

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("orders", orderCount);
        body.put("revenueCents", revenue);
        body.put("marginCents", margin);
        body.put("suppliers", ((Number) r[3]).longValue());
        body.put("products", ((Number) r[4]).longValue());
        body.put("customers", ((Number) r[5]).longValue());
        body.put("marginPercent", revenue == 0 ? 0.0 : Math.round((margin * 1000.0) / revenue) / 10.0);
        return body;
    }

    private String slug(String name) {
        String base = name.toLowerCase().replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
        return base + "-" + UUID.randomUUID().toString().substring(0, 6);
    }
}
