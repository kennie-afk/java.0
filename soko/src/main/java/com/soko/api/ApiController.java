package com.soko.api;

import com.soko.domain.*;
import com.soko.inventory.WastageService;
import com.soko.persistence.*;
import com.soko.platform.Errors;
import com.soko.routing.OrderService;
import com.soko.security.PasswordResetService;
import com.soko.security.Principal;
import com.soko.security.tenant.TenantBinding;
import com.soko.security.TenantContext;
import com.soko.security.Tokens;
import com.soko.security.UserStatusGate;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.*;
import java.util.stream.Collectors;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/v1")
public class ApiController {

    private final TenantRepository tenants;
    private final UserRepository users;
    private final SupplierRepository suppliers;
    private final ProductRepository products;
    private final OfferRepository offers;
    private final CustomerRepository customers;
    private final OrderRepository orders;
    private final OrderLineRepository orderLines;
    private final OrderService orderService;
    private final WastageService wastageService;
    private final WastageRecordRepository wastageRecords;
    private final PasswordEncoder encoder;
    private final Tokens tokens;
    private final TenantContext context;
    private final UserStatusGate statusGate;
    private final PasswordResetService passwordResets;

    public ApiController(
            TenantRepository tenants, UserRepository users, SupplierRepository suppliers,
            ProductRepository products, OfferRepository offers, CustomerRepository customers,
            OrderRepository orders, OrderLineRepository orderLines, OrderService orderService,
            WastageService wastageService, WastageRecordRepository wastageRecords,
            PasswordEncoder encoder, Tokens tokens, TenantContext context,
            UserStatusGate statusGate, PasswordResetService passwordResets) {
        this.tenants = tenants; this.users = users; this.suppliers = suppliers;
        this.products = products; this.offers = offers; this.customers = customers;
        this.orders = orders; this.orderLines = orderLines; this.orderService = orderService;
        this.wastageService = wastageService;
        this.wastageRecords = wastageRecords;
        this.encoder = encoder; this.tokens = tokens; this.context = context;
        this.statusGate = statusGate;
        this.passwordResets = passwordResets;
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
        // No tenant exists yet, and the e-mail check must see every tenant's accounts.
        return TenantBinding.asSystem(() -> registerAsSystem(request));
    }

    private Session registerAsSystem(RegisterRequest request) {
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
        // Signing in is how the tenant becomes known, so the lookup is by e-mail across tenants.
        return TenantBinding.asSystem(() -> {
            AppUser user = users.findByEmailAndStatus(request.email(), "ACTIVE")
                    .orElseThrow(() -> new Errors.Unauthorized("those credentials are not valid"));
            if (!encoder.matches(request.password(), user.getPasswordHash())) {
                throw new Errors.Unauthorized("those credentials are not valid");
            }
            return session(user);
        });
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
        // An e-mail is unique across tenants, so the check cannot be limited to this one.
        if (TenantBinding.asSystem(() -> users.findByEmailAndStatus(request.email(), "ACTIVE")).isPresent()) {
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

    @PostMapping("/users/{id}/suspend")
    public Map<String, Object> suspendUser(@PathVariable UUID id) {
        return changeStatus(id, "SUSPENDED");
    }

    @PostMapping("/users/{id}/reactivate")
    public Map<String, Object> reactivateUser(@PathVariable UUID id) {
        return changeStatus(id, "ACTIVE");
    }

    private Map<String, Object> changeStatus(UUID id, String status) {
        Principal caller = context.current();
        if (!"OWNER".equals(caller.role())) {
            throw new Errors.Unauthorized("only an owner can suspend or reactivate accounts");
        }
        AppUser user = users.findById(id)
                .filter(found -> found.getTenantId().equals(caller.tenantId()))
                .orElseThrow(() -> new Errors.NotFound("no such account"));
        if ("SUSPENDED".equals(status)) {
            if (user.getId().equals(caller.userId())) {
                throw new Errors.BadRequest("you cannot suspend your own account");
            }
            if ("OWNER".equals(user.getRole())
                    && users.findByTenantIdAndRole(caller.tenantId(), "OWNER").stream()
                            .filter(owner -> "ACTIVE".equals(owner.getStatus()))
                            .count() <= 1) {
                throw new Errors.BadRequest("the last active owner cannot be suspended");
            }
        }
        user.setStatus(status);
        users.save(user);
        statusGate.forget(user.getId());
        return Map.of("id", user.getId(), "status", user.getStatus());
    }

    @PostMapping("/auth/forgot")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public Acknowledged forgot(@Valid @RequestBody ForgotRequest request) {
        TenantBinding.asSystem(() -> passwordResets.issue(request.email()))
                .ifPresent(passwordResets::deliver);
        return new Acknowledged(
                "If that email has an account, a reset link is on its way.");
    }

    public record ResetRequest(@NotBlank String token, @Size(min = 10) String password) {}

    @PostMapping("/auth/reset")
    public Acknowledged resetPassword(@Valid @RequestBody ResetRequest request) {
        TenantBinding.asSystem(() -> passwordResets.reset(request.token(), request.password()));
        return new Acknowledged("Your password has been changed. Sign in with the new one.");
    }

    public record SupplierRequest(
            @NotBlank String name, @NotBlank String county,
            @Min(1) int leadTimeHours, boolean coldChain, Double reliability) {}

    public record SupplierUpdate(
            String name, String county, Integer leadTimeHours, Boolean coldChain,
            Double reliability, String status) {}

    @GetMapping("/suppliers/{id}")
    public Supplier getSupplier(@PathVariable UUID id) {
        return suppliers.findByIdAndTenantId(id, context.current().tenantId())
                .orElseThrow(() -> new Errors.NotFound("no such supplier"));
    }

    private static java.math.BigDecimal reliability(double value) {
        // numeric(4,3): anything outside 0..1 is a typo (90 instead of 0.9) that would overflow the column.
        if (Double.isNaN(value) || value < 0 || value > 1) {
            throw new Errors.BadRequest("reliability must be between 0 and 1");
        }
        return java.math.BigDecimal.valueOf(value);
    }

    @PatchMapping("/suppliers/{id}")
    public Supplier updateSupplier(@PathVariable UUID id, @RequestBody SupplierUpdate request) {
        Supplier supplier = suppliers.findByIdAndTenantId(id, context.current().tenantId())
                .orElseThrow(() -> new Errors.NotFound("no such supplier"));
        if (request.name() != null) supplier.setName(request.name());
        if (request.county() != null) supplier.setCounty(request.county());
        if (request.leadTimeHours() != null) {
            if (request.leadTimeHours() < 1) {
                throw new Errors.BadRequest("lead time must be at least 1 hour");
            }
            supplier.setLeadTimeHours(request.leadTimeHours());
        }
        if (request.coldChain() != null) supplier.setColdChain(request.coldChain());
        if (request.reliability() != null) {
            supplier.setReliability(reliability(request.reliability()));
        }
        if (request.status() != null) {
            String status = request.status().toUpperCase();
            if (!List.of("ACTIVE", "INACTIVE").contains(status)) {
                throw new Errors.BadRequest("status must be ACTIVE or INACTIVE");
            }
            supplier.setStatus(status);
        }
        return suppliers.save(supplier);
    }

    @GetMapping("/suppliers")
    public ResponseEntity<List<Supplier>> listSuppliers(
            @RequestParam(defaultValue = "50") int limit,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "") String q) {
        var found = suppliers.search(context.current().tenantId(), Paging.like(q),
                Paging.pageable(page, limit));
        return Paging.respond(found.getContent(), found.getTotalElements(), page, limit);
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
            supplier.setReliability(reliability(request.reliability()));
        }
        return suppliers.save(supplier);
    }

    public record ProductRequest(
            @NotBlank String sku, @NotBlank String name, @NotBlank String category,
            @NotBlank String unit, boolean perishable, boolean requiresColdChain,
            @Min(1) int shelfLifeHours, @Min(1) long listPriceCents, String photoUrl) {}

    public record ProductUpdate(
            String name, String category, Long listPriceCents, Integer shelfLifeHours,
            String photoUrl) {}

    @PatchMapping("/products/{id}")
    public Product updateProduct(@PathVariable UUID id, @RequestBody ProductUpdate request) {
        Product product = products.findByIdAndTenantId(id, context.current().tenantId())
                .orElseThrow(() -> new Errors.NotFound("no such product"));
        if (request.name() != null) product.setName(request.name());
        if (request.category() != null) product.setCategory(request.category());
        if (request.listPriceCents() != null) {
            if (request.listPriceCents() < 1) {
                throw new Errors.BadRequest("list price must be positive");
            }
            product.setListPriceCents(request.listPriceCents());
        }
        if (request.shelfLifeHours() != null) {
            if (request.shelfLifeHours() < 1) {
                throw new Errors.BadRequest("shelf life must be at least 1 hour");
            }
            product.setShelfLifeHours(request.shelfLifeHours());
        }
        if (request.photoUrl() != null) product.setPhotoUrl(request.photoUrl());
        return products.save(product);
    }

    @GetMapping("/products/{id}")
    public Product getProduct(@PathVariable UUID id) {
        return products.findByIdAndTenantId(id, context.current().tenantId())
                .orElseThrow(() -> new Errors.NotFound("no such product"));
    }

    @GetMapping("/products")
    public ResponseEntity<List<Product>> listProducts(
            @RequestParam(defaultValue = "50") int limit,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "") String q) {
        var found = products.search(context.current().tenantId(), Paging.like(q),
                Paging.pageable(page, limit));
        return Paging.respond(found.getContent(), found.getTotalElements(), page, limit);
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
        product.setPhotoUrl(request.photoUrl());
        return products.save(product);
    }

    public record OfferRequest(
            @NotNull UUID supplierId, @NotNull UUID productId,
            @Min(1) long costCents, @Min(0) int availableQty) {}

    @GetMapping("/offers")
    public ResponseEntity<List<Map<String, Object>>> listOffers(
            @RequestParam(defaultValue = "50") int limit,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "") String q) {
        UUID tenantId = context.current().tenantId();
        var found = offers.search(tenantId, Paging.like(q), Paging.pageable(page, limit));
        List<Offer> offerPage = found.getContent();

        Map<UUID, Supplier> supplierIndex =
                suppliers.findByIdIn(offerPage.stream().map(Offer::getSupplierId).distinct().toList())
                        .stream().collect(Collectors.toMap(Supplier::getId, s -> s));
        Map<UUID, Product> productIndex =
                products.findByIdIn(offerPage.stream().map(Offer::getProductId).distinct().toList())
                        .stream().collect(Collectors.toMap(Product::getId, p -> p));

        List<Map<String, Object>> rows = offerPage.stream()
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
        return Paging.respond(rows, found.getTotalElements(), page, limit);
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
    public ResponseEntity<List<Customer>> listCustomers(
            @RequestParam(defaultValue = "50") int limit,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "") String q) {
        var found = customers.search(context.current().tenantId(), Paging.like(q),
                Paging.pageable(page, limit));
        return Paging.respond(found.getContent(), found.getTotalElements(), page, limit);
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
    public OrderService.Placed placeOrder(
            @Valid @RequestBody OrderRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        List<OrderService.LineRequest> lines = request.lines().stream()
                .map(l -> new OrderService.LineRequest(l.productId(), l.quantity()))
                .toList();
        return orderService.place(context.current().tenantId(), request.customerId(), lines, false,
                idempotencyKey);
    }

    @GetMapping("/orders")
    public ResponseEntity<List<Map<String, Object>>> listOrders(
            @RequestParam(defaultValue = "50") int limit,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "") String q) {
        UUID tenantId = context.current().tenantId();
        int size = Paging.limit(limit);
        String pattern = Paging.like(q);
        List<Map<String, Object>> rows = orders
                .listWithCustomer(tenantId, pattern, size, (long) Paging.page(page) * size).stream()
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
        return Paging.respond(rows, orders.countWithCustomer(tenantId, pattern), page, size);
    }

    @GetMapping("/orders/{id}")
    public Map<String, Object> getOrder(@PathVariable UUID id) {
        UUID tenantId = context.current().tenantId();
        SalesOrder order = orders.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new Errors.NotFound("no such order"));
        List<OrderLine> lines = orderLines.findByOrderId(order.getId());
        Map<UUID, Product> productIndex =
                products.findByIdIn(lines.stream().map(OrderLine::getProductId).distinct().toList())
                        .stream().collect(Collectors.toMap(Product::getId, p -> p));
        Map<UUID, Supplier> supplierIndex =
                suppliers.findByIdIn(lines.stream().map(OrderLine::getSupplierId).distinct().toList())
                        .stream().collect(Collectors.toMap(Supplier::getId, s -> s));

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("id", order.getId());
        body.put("reference", order.getReference());
        body.put("status", order.getStatus());
        body.put("cancelReason", order.getCancelReason());
        body.put("placedAt", order.getPlacedAt());
        body.put("revenueCents", order.getRevenueCents());
        body.put("costCents", order.getCostCents());
        body.put("marginCents", order.getMarginCents());
        body.put("lines", lines.stream().map(l -> {
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

    public record CancelRequest(String reason) {}

    @PostMapping("/orders/{id}/cancel")
    public Map<String, Object> cancelOrder(@PathVariable UUID id, @RequestBody(required = false) CancelRequest request) {
        String reason = request == null ? null : request.reason();
        orderService.cancel(context.current().tenantId(), id, reason);
        return Map.of("id", id, "status", "CANCELLED");
    }

    public record WastageRequest(@NotNull UUID offerId, @Min(1) int quantity, String reason) {}

    @PostMapping("/wastage")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> recordWastage(@Valid @RequestBody WastageRequest request) {
        WastageRecord record = wastageService.record(
                context.current().tenantId(), request.offerId(), request.quantity(), request.reason());
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("id", record.getId());
        body.put("quantity", record.getQuantity());
        body.put("reason", record.getReason());
        body.put("valueCents", record.getValueCents());
        body.put("recordedAt", record.getRecordedAt());
        return body;
    }

    @GetMapping("/wastage")
    public Map<String, Object> wastageSummary() {
        return Map.of("totalValueCents", wastageService.totalValueCents(context.current().tenantId()));
    }

    @GetMapping("/wastage/records")
    public List<Map<String, Object>> wastageRecords(@RequestParam(defaultValue = "50") int limit) {
        return wastageRecords.listDetailed(context.current().tenantId(), Math.min(Math.max(limit, 1), 200)).stream()
                .map(r -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("id", r[0]);
                    row.put("product", r[1]);
                    row.put("supplier", r[2]);
                    row.put("quantity", ((Number) r[3]).intValue());
                    row.put("reason", r[4]);
                    row.put("valueCents", ((Number) r[5]).longValue());
                    row.put("recordedAt", r[6]);
                    return row;
                })
                .toList();
    }

    /** Weekly revenue, margin and cancellations for the last N weeks (default 12, max 52). */
    @GetMapping("/overview/trend")
    public List<Map<String, Object>> trend(@RequestParam(defaultValue = "12") int weeks) {
        int span = Math.min(Math.max(weeks, 1), 52);
        java.time.Instant since = java.time.Instant.now().minus(java.time.Duration.ofDays(7L * span));
        return orders.weekly(context.current().tenantId(), since).stream()
                .map(r -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("weekStart", ((java.time.Instant) toInstant(r[0])).toString());
                    row.put("orders", ((Number) r[1]).longValue());
                    row.put("revenueCents", ((Number) r[2]).longValue());
                    row.put("marginCents", ((Number) r[3]).longValue());
                    row.put("cancelled", ((Number) r[4]).longValue());
                    return row;
                })
                .toList();
    }

    private static java.time.Instant toInstant(Object value) {
        if (value instanceof java.time.Instant i) return i;
        if (value instanceof java.sql.Timestamp t) return t.toInstant();
        if (value instanceof java.time.OffsetDateTime o) return o.toInstant();
        throw new IllegalStateException("unexpected timestamp type " + value.getClass());
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
        body.put("wastageValueCents", wastageService.totalValueCents(context.current().tenantId()));
        return body;
    }

    private String slug(String name) {
        String base = name.toLowerCase().replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
        return base + "-" + UUID.randomUUID().toString().substring(0, 6);
    }
}
