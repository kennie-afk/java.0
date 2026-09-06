package com.soko.routing;

import com.soko.domain.*;
import com.soko.persistence.*;
import com.soko.platform.Errors;
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
            RoutingEngine engine) {
        this.products = products;
        this.offers = offers;
        this.suppliers = suppliers;
        this.customers = customers;
        this.orders = orders;
        this.orderLines = orderLines;
        this.engine = engine;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean reserve(UUID offerId, int quantity) {
        return offers.reserve(offerId, quantity) == 1;
    }

    @Transactional
    public Placed place(UUID tenantId, UUID customerId, List<LineRequest> requested) {
        return place(tenantId, customerId, requested, false);
    }

    @Transactional
    public Placed place(
            UUID tenantId, UUID customerId, List<LineRequest> requested, boolean byCustomer) {
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
        order = orders.saveAndFlush(order);

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

        return new Placed(
                order.getId(), order.getReference(), revenue, cost, revenue - cost, placedLines);
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
