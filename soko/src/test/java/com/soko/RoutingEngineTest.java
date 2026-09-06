package com.soko;

import static org.assertj.core.api.Assertions.assertThat;

import com.soko.domain.Offer;
import com.soko.domain.Product;
import com.soko.domain.Supplier;
import com.soko.routing.RoutingEngine;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RoutingEngineTest {

    private final RoutingEngine engine = new RoutingEngine();

    private Supplier supplier(String name, int leadTimeHours, boolean coldChain, String reliability) {
        Supplier s = new Supplier();
        s.setId(UUID.randomUUID());
        s.setName(name);
        s.setCounty("Nakuru");
        s.setLeadTimeHours(leadTimeHours);
        s.setColdChain(coldChain);
        s.setReliability(new BigDecimal(reliability));
        s.setStatus("ACTIVE");
        return s;
    }

    private Product product(boolean perishable, boolean coldChain, int shelfLifeHours, long price) {
        Product p = new Product();
        p.setId(UUID.randomUUID());
        p.setName("Fresh milk 500ml");
        p.setPerishable(perishable);
        p.setRequiresColdChain(coldChain);
        p.setShelfLifeHours(shelfLifeHours);
        p.setListPriceCents(price);
        return p;
    }

    private Offer offer(Supplier s, Product p, long cost, int qty) {
        Offer o = new Offer();
        o.setId(UUID.randomUUID());
        o.setSupplierId(s.getId());
        o.setProductId(p.getId());
        o.setCostCents(cost);
        o.setAvailableQty(qty);
        o.setStatus("ACTIVE");
        return o;
    }

    @Test
    void picks_the_cheapest_viable_supplier() {
        Supplier cheap = supplier("Cheap Dairy", 6, true, "0.900");
        Supplier dear = supplier("Dear Dairy", 6, true, "0.990");
        Product milk = product(true, true, 48, 8000);

        var outcome = engine.route(milk, 10,
                List.of(offer(dear, milk, 7000, 100), offer(cheap, milk, 5000, 100)),
                Map.of(cheap.getId(), cheap, dear.getId(), dear));

        assertThat(outcome.decision()).isPresent();
        assertThat(outcome.decision().get().supplier().getName()).isEqualTo("Cheap Dairy");
    }

    @Test
    void refuses_a_supplier_without_cold_chain_for_a_chilled_product() {
        Supplier warm = supplier("Warm Transport", 4, false, "0.990");
        Product milk = product(true, true, 48, 8000);

        var outcome = engine.route(milk, 5, List.of(offer(warm, milk, 1000, 100)),
                Map.of(warm.getId(), warm));

        assertThat(outcome.decision()).isEmpty();
        assertThat(outcome.rejected()).hasSize(1);
        assertThat(outcome.rejected().get(0).reason()).contains("cold chain");
    }

    @Test
    void refuses_a_supplier_whose_lead_time_outlasts_shelf_life() {
        Supplier slow = supplier("Slow Haulage", 72, true, "0.990");
        Product milk = product(true, true, 48, 8000);

        var outcome = engine.route(milk, 5, List.of(offer(slow, milk, 1000, 100)),
                Map.of(slow.getId(), slow));

        assertThat(outcome.decision()).isEmpty();
        assertThat(outcome.rejected().get(0).reason()).contains("shelf life");
    }

    @Test
    void a_non_perishable_product_tolerates_a_long_lead_time() {
        Supplier slow = supplier("Slow Haulage", 72, false, "0.900");
        Product potatoes = product(false, false, 24, 5000);

        var outcome = engine.route(potatoes, 5, List.of(offer(slow, potatoes, 1000, 100)),
                Map.of(slow.getId(), slow));

        assertThat(outcome.decision()).isPresent();
    }

    @Test
    void refuses_a_supplier_that_cannot_cover_the_quantity() {
        Supplier small = supplier("Small Farm", 4, true, "0.990");
        Product milk = product(true, true, 48, 8000);

        var outcome = engine.route(milk, 500, List.of(offer(small, milk, 1000, 20)),
                Map.of(small.getId(), small));

        assertThat(outcome.decision()).isEmpty();
        assertThat(outcome.rejected().get(0).reason()).contains("quantity");
    }

    @Test
    void reliability_breaks_a_price_tie() {
        Supplier reliable = supplier("Reliable", 6, true, "0.990");
        Supplier flaky = supplier("Flaky", 6, true, "0.500");
        Product milk = product(true, true, 48, 8000);

        var outcome = engine.route(milk, 10,
                List.of(offer(flaky, milk, 5000, 100), offer(reliable, milk, 5000, 100)),
                Map.of(reliable.getId(), reliable, flaky.getId(), flaky));

        assertThat(outcome.decision().get().supplier().getName()).isEqualTo("Reliable");
    }

    @Test
    void an_inactive_supplier_is_skipped() {
        Supplier suspended = supplier("Suspended", 4, true, "0.990");
        suspended.setStatus("SUSPENDED");
        Product milk = product(true, true, 48, 8000);

        var outcome = engine.route(milk, 5, List.of(offer(suspended, milk, 1000, 100)),
                Map.of(suspended.getId(), suspended));

        assertThat(outcome.decision()).isEmpty();
        assertThat(outcome.rejected().get(0).reason()).contains("not active");
    }

    @Test
    void no_offers_means_no_decision() {
        Product milk = product(true, true, 48, 8000);
        var outcome = engine.route(milk, 5, List.of(), Map.of());
        assertThat(outcome.decision()).isEmpty();
        assertThat(outcome.rejected()).isEmpty();
    }

    @Test
    void margin_is_the_spread_times_quantity() {
        assertThat(engine.marginCents(8000, 5000, 12)).isEqualTo(36000);
    }
}
