package com.soko.routing;

import com.soko.billing.CommissionService;
import com.soko.domain.*;
import com.soko.persistence.*;
import com.soko.platform.Errors;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrderService {

    public record LineRequest(UUID productId, int quantity) {}

    public record PlacedLine(
            UUID lineId,
            UUID productId,
            String productName,
            int quantity,
            UUID supplierId,
            String supplierName,
            long unitPriceCents,
            long unitCostCents,
            long marginCents,
            String routingReason) {}

    public record Placed(
            UUID orderId,
            String reference,
            long revenueCents,
            long costCents,
            long marginCents,
            List<PlacedLine> lines) {}

    private final ProductRepository products;
    private final OfferRepository offers;
    private final SupplierRepository suppliers;
    private final CustomerRepository customers;
    private final OrderRepository orders;
    private final OrderLineRepository orderLines;
    private final RoutingEngine engine;
    private final com.soko.notifications.OrderNotifications notifications;
    private final CommissionService commissions;
    private OrderService self;

    @org.springframework.beans.factory.annotation.Autowired
    public void setSelf(@org.springframework.context.annotation.Lazy OrderService self) {
        this.self = self;
    }

    public OrderService(
            ProductRepository products,
            OfferRepository offers,
            SupplierRepository suppliers,
            CustomerRepository customers,
            OrderRepository orders,
            OrderLineRepository orderLines,
            RoutingEngine engine,
            com.soko.notifications.OrderNotifications notifications,
            CommissionService commissions) {
        this.products = products;
        this.offers = offers;
        this.suppliers = suppliers;
        this.customers = customers;
        this.orders = orders;
        this.orderLines = orderLines;
        this.engine = engine;
        this.notifications = notifications;
        this.commissions = commissions;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean reserve(UUID offerId, int quantity) {
        return offers.reserve(offerId, quantity) == 1;
    }

    /** Puts reserved stock back after a failed placement; its own transaction, like {@link #reserve}. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void release(UUID offerId, int quantity) {
        offers.restock(offerId, quantity);
    }

    @Transactional
    public Placed place(UUID tenantId, UUID customerId, List<LineRequest> requested) {
        return place(tenantId, customerId, requested, false);
    }

    @Transactional
    public Placed place(
            UUID tenantId, UUID customerId, List<LineRequest> requested, boolean byCustomer) {
        return placeKeyed(tenantId, customerId, requested, byCustomer, null);
    }

    /**
     * Places an order at most once per idempotency key. A retry (a double tap, a client timeout
     * followed by a resend) returns the order the first request created and reserves nothing; a
     * key reused for a different customer or basket is refused rather than silently answered with
     * an unrelated order. Two duplicates racing each other are settled by the unique index on
     * (tenant, key): the loser's insert fails before it reserves any stock, and it then replays
     * the winner's order.
     *
     * <p>Not transactional itself, on purpose: a unique violation aborts the transaction it
     * happens in, so the lookup that follows has to run in a fresh one.
     */
    public Placed place(UUID tenantId, UUID customerId, List<LineRequest> requested,
            boolean byCustomer, String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            return self.placeKeyed(tenantId, customerId, requested, byCustomer, null);
        }
        if (idempotencyKey.length() > 80) {
            throw new Errors.BadRequest("Idempotency-Key is longer than 80 characters");
        }
        Optional<Placed> earlier = self.replay(tenantId, customerId, requested, idempotencyKey);
        if (earlier.isPresent()) {
            return earlier.get();
        }
        try {
            return self.placeKeyed(tenantId, customerId, requested, byCustomer, idempotencyKey);
        } catch (org.springframework.dao.DataIntegrityViolationException duplicate) {
            return self.replay(tenantId, customerId, requested, idempotencyKey)
                    .orElseThrow(() -> duplicate);
        }
    }

    /** The order already placed under this key, rebuilt from storage, or empty when there is none. */
    @Transactional(readOnly = true)
    public Optional<Placed> replay(UUID tenantId, UUID customerId, List<LineRequest> requested,
            String idempotencyKey) {
        Optional<SalesOrder> found = orders.findByTenantIdAndIdempotencyKey(tenantId, idempotencyKey);
        if (found.isEmpty()) {
            return Optional.empty();
        }
        SalesOrder order = found.get();
        List<OrderLine> stored = orderLines.findByOrderId(order.getId());
        if (!order.getCustomerId().equals(customerId) || !sameBasket(requested, stored)) {
            throw new Errors.BadRequest(
                    "that Idempotency-Key was already used for a different order");
        }
        Map<UUID, Product> productIndex = products
                .findByIdIn(stored.stream().map(OrderLine::getProductId).distinct().toList())
                .stream().collect(Collectors.toMap(Product::getId, p -> p));
        Map<UUID, Supplier> supplierIndex = suppliers
                .findByIdIn(stored.stream().map(OrderLine::getSupplierId)
                        .filter(Objects::nonNull).distinct().toList())
                .stream().collect(Collectors.toMap(Supplier::getId, s -> s));
        List<PlacedLine> lines = stored.stream().map(line -> {
            Product product = productIndex.get(line.getProductId());
            Supplier supplier = supplierIndex.get(line.getSupplierId());
            return new PlacedLine(line.getId(), line.getProductId(),
                    product == null ? null : product.getName(), line.getQuantity(),
                    line.getSupplierId(), supplier == null ? null : supplier.getName(),
                    line.getUnitPriceCents(), line.getUnitCostCents(),
                    (line.getUnitPriceCents() - line.getUnitCostCents()) * line.getQuantity(),
                    line.getRoutingReason());
        }).toList();
        return Optional.of(new Placed(order.getId(), order.getReference(), order.getRevenueCents(),
                order.getCostCents(), order.getMarginCents(), lines));
    }

    private static boolean sameBasket(List<LineRequest> requested, List<OrderLine> stored) {
        Map<UUID, Integer> want = new HashMap<>();
        requested.forEach(l -> want.merge(l.productId(), l.quantity(), Integer::sum));
        Map<UUID, Integer> have = new HashMap<>();
        stored.forEach(l -> have.merge(l.getProductId(), l.getQuantity(), Integer::sum));
        return want.equals(have);
    }

    @Transactional
    public Placed placeKeyed(UUID tenantId, UUID customerId, List<LineRequest> requested,
            boolean byCustomer, String idempotencyKey) {
        if (requested == null || requested.isEmpty()) {
            throw new Errors.BadRequest("an order needs at least one line");
        }

        Customer customer =
                customers
                        .findByIdAndTenantId(customerId, tenantId)
                        .orElseThrow(() -> new Errors.NotFound("no such customer"));

        SalesOrder order = new SalesOrder();
        order.setTenantId(tenantId);
        order.setCustomerId(customer.getId());
        order.setReference(reference());
        order.setPlacedByCustomer(byCustomer);
        order.setIdempotencyKey(idempotencyKey);
        // The flush is what trips the unique index for a duplicate key, before any stock moves.
        order = orders.saveAndFlush(order);

        // reserve() commits on its own, so a failure after it would otherwise leave stock
        // reserved for an order that was rolled back: put it back before rethrowing.
        List<Offer> reserved = new ArrayList<>();
        List<Integer> reservedQty = new ArrayList<>();
        try {
            long revenue = 0;
            long cost = 0;
            List<PlacedLine> placedLines = new ArrayList<>();

            for (LineRequest line : requested) {
                if (line.quantity() <= 0) {
                    throw new Errors.BadRequest("a line quantity must be positive");
                }

                Product product =
                        products
                                .findByIdAndTenantId(line.productId(), tenantId)
                                .orElseThrow(() -> new Errors.NotFound("no such product"));

                List<Offer> candidates =
                        offers.candidates(tenantId, product.getId(), line.quantity());

                Map<UUID, Supplier> supplierIndex =
                        candidates.isEmpty()
                                ? Map.of()
                                : suppliers
                                        .findByIdIn(
                                                candidates.stream().map(Offer::getSupplierId).toList())
                                        .stream()
                                        .collect(Collectors.toMap(Supplier::getId, s -> s));

                RoutingEngine.Outcome outcome =
                        engine.route(product, line.quantity(), candidates, supplierIndex);

                RoutingEngine.Decision decision =
                        outcome.decision()
                                .orElseThrow(
                                        () ->
                                                new Errors.Unroutable(
                                                        "no supplier can fulfil "
                                                                + product.getName()
                                                                + ": "
                                                                + describe(outcome.rejected())));

                Offer offer = decision.offer();
                if (!self.reserve(offer.getId(), line.quantity())) {
                    throw new Errors.Unroutable(
                            "stock for " + product.getName() + " was taken by another order");
                }
                reserved.add(offer);
                reservedQty.add(line.quantity());

                OrderLine stored = new OrderLine();
                stored.setTenantId(tenantId);
                stored.setOrderId(order.getId());
                stored.setProductId(product.getId());
                stored.setSupplierId(decision.supplier().getId());
                stored.setQuantity(line.quantity());
                stored.setUnitPriceCents(product.getListPriceCents());
                stored.setUnitCostCents(offer.getCostCents());
                stored.setStatus("ROUTED");
                stored.setRoutingReason(decision.reason());
                stored = orderLines.save(stored);

                long lineRevenue = product.getListPriceCents() * line.quantity();
                long lineCost = offer.getCostCents() * line.quantity();
                revenue += lineRevenue;
                cost += lineCost;

                placedLines.add(
                        new PlacedLine(
                                stored.getId(),
                                product.getId(),
                                product.getName(),
                                line.quantity(),
                                decision.supplier().getId(),
                                decision.supplier().getName(),
                                product.getListPriceCents(),
                                offer.getCostCents(),
                                lineRevenue - lineCost,
                                decision.reason()));
            }

            order.setRevenueCents(revenue);
            order.setCostCents(cost);
            order.setMarginCents(revenue - cost);
            orders.save(order);

            commissions.accrue(tenantId, order.getId(), revenue);
            notifications.orderPlaced(tenantId, customer.getId(), order.getReference(), revenue);

            return new Placed(
                    order.getId(), order.getReference(), revenue, cost, revenue - cost, placedLines);
        } catch (RuntimeException failure) {
            for (int i = 0; i < reserved.size(); i++) {
                try {
                    self.release(reserved.get(i).getId(), reservedQty.get(i));
                } catch (RuntimeException ignored) {
                    // best effort: the original failure is the one worth reporting
                }
            }
            throw failure;
        }
    }

    /**
     * A ROUTED order that hasn't been paid or dispatched can still be
     * cancelled cleanly: every reserved offer is restocked and the platform
     * commission accrued on it is voided. Once payment or dispatch has
     * started, this is refused -- unwinding a paid order or one already on
     * its way needs a real refund/return flow, not a silent status flip.
     */
    @Transactional
    public void cancel(UUID tenantId, UUID orderId, String reason) {
        SalesOrder order = orders.findByIdAndTenantId(orderId, tenantId)
                .orElseThrow(() -> new Errors.NotFound("no such order"));

        if (!"ROUTED".equals(order.getStatus())) {
            throw new Errors.BadRequest(
                    "only a routed, unpaid order can be cancelled (this one is "
                            + order.getStatus() + ")");
        }

        for (OrderLine line : orderLines.findByOrderId(orderId)) {
            if (line.getSupplierId() != null) {
                offers.findBySupplierIdAndProductId(line.getSupplierId(), line.getProductId())
                        .ifPresent(offer -> offers.restock(offer.getId(), line.getQuantity()));
            }
            line.setStatus("CANCELLED");
            orderLines.save(line);
        }

        order.setStatus("CANCELLED");
        order.setCancelledAt(Instant.now());
        order.setCancelReason(reason);
        orders.save(order);

        commissions.voidForOrder(orderId);
    }

    private String describe(List<RoutingEngine.Rejection> rejected) {
        if (rejected.isEmpty()) {
            return "no supplier offers this product";
        }
        return rejected.stream()
                .map(RoutingEngine.Rejection::reason)
                .distinct()
                .collect(Collectors.joining("; "));
    }

    private String reference() {
        return "SO-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }
}
