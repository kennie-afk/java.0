package com.soko;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.soko.billing.CommissionService;
import com.soko.domain.Customer;
import com.soko.domain.Offer;
import com.soko.domain.OrderLine;
import com.soko.domain.Product;
import com.soko.domain.SalesOrder;
import com.soko.domain.Supplier;
import com.soko.notifications.OrderNotifications;
import com.soko.persistence.*;
import com.soko.platform.Errors;
import com.soko.routing.OrderService;
import com.soko.routing.RoutingEngine;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

/** Idempotent placement and stock hygiene, with the repositories faked. */
class OrderServiceIdempotencyTest {

    private final ProductRepository products = mock(ProductRepository.class);
    private final OfferRepository offers = mock(OfferRepository.class);
    private final SupplierRepository suppliers = mock(SupplierRepository.class);
    private final CustomerRepository customers = mock(CustomerRepository.class);
    private final OrderRepository orders = mock(OrderRepository.class);
    private final OrderLineRepository orderLines = mock(OrderLineRepository.class);
    private final OrderService service = new OrderService(products, offers, suppliers, customers,
            orders, orderLines, new RoutingEngine(), mock(OrderNotifications.class),
            mock(CommissionService.class));

    private final UUID tenant = UUID.randomUUID();
    private final UUID customerId = UUID.randomUUID();
    private Supplier supplier;
    private Product milk;
    private Offer milkOffer;

    @BeforeEach
    void world() {
        service.setSelf(service);
        Customer customer = new Customer();
        customer.setId(customerId);
        customer.setTenantId(tenant);
        when(customers.findByIdAndTenantId(customerId, tenant)).thenReturn(Optional.of(customer));

        supplier = new Supplier();
        supplier.setId(UUID.randomUUID());
        supplier.setName("Farm");
        supplier.setLeadTimeHours(5);
        when(suppliers.findByIdIn(any())).thenReturn(List.of(supplier));

        milk = product("Milk");
        milkOffer = offer(milk);

        when(orders.saveAndFlush(any(SalesOrder.class))).thenAnswer(call -> {
            SalesOrder order = call.getArgument(0);
            order.setId(UUID.randomUUID());
            return order;
        });
        when(orderLines.save(any(OrderLine.class))).thenAnswer(call -> {
            OrderLine line = call.getArgument(0);
            line.setId(UUID.randomUUID());
            return line;
        });
    }

    private Product product(String name) {
        Product p = new Product();
        p.setId(UUID.randomUUID());
        p.setTenantId(tenant);
        p.setName(name);
        p.setShelfLifeHours(720);
        p.setListPriceCents(10_000);
        when(products.findByIdAndTenantId(p.getId(), tenant)).thenReturn(Optional.of(p));
        when(products.findByIdIn(any())).thenReturn(List.of(p));
        return p;
    }

    private Offer offer(Product p) {
        Offer o = new Offer();
        o.setId(UUID.randomUUID());
        o.setTenantId(tenant);
        o.setSupplierId(supplier.getId());
        o.setProductId(p.getId());
        o.setCostCents(6_000);
        o.setAvailableQty(100);
        when(offers.candidates(eq(tenant), eq(p.getId()), anyInt())).thenReturn(List.of(o));
        when(offers.reserve(eq(o.getId()), anyInt())).thenReturn(1);
        return o;
    }

    private SalesOrder stored(String key, int qty) {
        SalesOrder order = new SalesOrder();
        order.setId(UUID.randomUUID());
        order.setTenantId(tenant);
        order.setCustomerId(customerId);
        order.setReference("SO-ORIG");
        order.setRevenueCents(10_000L * qty);
        order.setCostCents(6_000L * qty);
        order.setMarginCents(4_000L * qty);
        order.setIdempotencyKey(key);
        OrderLine line = new OrderLine();
        line.setId(UUID.randomUUID());
        line.setProductId(milk.getId());
        line.setSupplierId(supplier.getId());
        line.setQuantity(qty);
        line.setUnitPriceCents(10_000);
        line.setUnitCostCents(6_000);
        when(orders.findByTenantIdAndIdempotencyKey(tenant, key)).thenReturn(Optional.of(order));
        when(orderLines.findByOrderId(order.getId())).thenReturn(List.of(line));
        return order;
    }

    private List<OrderService.LineRequest> basket(int qty) {
        return List.of(new OrderService.LineRequest(milk.getId(), qty));
    }

    @Test
    void aRetryReturnsTheOriginalOrderAndReservesNothing() {
        SalesOrder original = stored("k1", 3);

        OrderService.Placed placed = service.place(tenant, customerId, basket(3), true, "k1");

        assertThat(placed.orderId()).isEqualTo(original.getId());
        assertThat(placed.reference()).isEqualTo("SO-ORIG");
        assertThat(placed.revenueCents()).isEqualTo(30_000);
        assertThat(placed.lines()).singleElement().satisfies(line -> {
            assertThat(line.productName()).isEqualTo("Milk");
            assertThat(line.supplierName()).isEqualTo("Farm");
        });
        verify(offers, never()).reserve(any(), anyInt());
        verify(orders, never()).saveAndFlush(any());
    }

    @Test
    void theFirstRequestWithAKeyPlacesTheOrderOnceAndRecordsTheKey() {
        OrderService.Placed placed = service.place(tenant, customerId, basket(2), true, "fresh");

        assertThat(placed.revenueCents()).isEqualTo(20_000);
        verify(offers, times(1)).reserve(milkOffer.getId(), 2);
        verify(orders).saveAndFlush(org.mockito.ArgumentMatchers.argThat(
                (SalesOrder o) -> "fresh".equals(o.getIdempotencyKey())));
    }

    @Test
    void twoRacingDuplicatesReserveStockOnceBecauseTheLoserFailsBeforeReserving() {
        // The loser's lookup finds nothing (the winner has not committed), its insert then trips
        // the unique index, and the second lookup finds the winner's order.
        SalesOrder winner = stored("race", 2);
        when(orders.findByTenantIdAndIdempotencyKey(tenant, "race"))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(winner));
        when(orders.saveAndFlush(any(SalesOrder.class)))
                .thenThrow(new DataIntegrityViolationException("uq_orders_idempotency"));

        OrderService.Placed placed = service.place(tenant, customerId, basket(2), true, "race");

        assertThat(placed.orderId()).isEqualTo(winner.getId());
        verify(offers, never()).reserve(any(), anyInt());
    }

    @Test
    void aKeyReusedForADifferentBasketOrCustomerIsRefused() {
        stored("reused", 3);

        assertThatThrownBy(() -> service.place(tenant, customerId, basket(4), true, "reused"))
                .isInstanceOf(Errors.BadRequest.class).hasMessageContaining("different order");
        assertThatThrownBy(() -> service.place(tenant, UUID.randomUUID(), basket(3), true, "reused"))
                .isInstanceOf(Errors.BadRequest.class);
        verify(offers, never()).reserve(any(), anyInt());
    }

    @Test
    void anOversizedKeyIsRefused() {
        assertThatThrownBy(() -> service.place(tenant, customerId, basket(1), true, "x".repeat(81)))
                .isInstanceOf(Errors.BadRequest.class);
    }

    @Test
    void anOrderWhoseLaterLineFailsGivesBackTheStockItAlreadyReserved() {
        Product eggs = product("Eggs");
        // Nobody offers eggs, so the second line is unroutable after the first reserved milk.
        when(offers.candidates(eq(tenant), eq(eggs.getId()), anyInt())).thenReturn(List.of());
        when(products.findByIdIn(any())).thenReturn(List.of(milk, eggs));

        assertThatThrownBy(() -> service.place(tenant, customerId, List.of(
                new OrderService.LineRequest(milk.getId(), 4),
                new OrderService.LineRequest(eggs.getId(), 1)), true, null))
                .isInstanceOf(Errors.Unroutable.class);

        verify(offers).reserve(milkOffer.getId(), 4);
        // reserve() commits on its own, so without this the 4 litres stay lost.
        verify(offers).restock(milkOffer.getId(), 4);
    }
}
