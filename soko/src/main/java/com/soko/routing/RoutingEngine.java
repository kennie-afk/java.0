package com.soko.routing;

import com.soko.domain.Offer;
import com.soko.domain.Product;
import com.soko.domain.Supplier;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class RoutingEngine {

    public record Decision(Offer offer, Supplier supplier, String reason) {}

    public record Rejection(UUID supplierId, String reason) {}

    public record Outcome(Optional<Decision> decision, List<Rejection> rejected) {}

    public Outcome route(
            Product product, int quantity, List<Offer> candidates, Map<UUID, Supplier> suppliers) {

        List<Rejection> rejected = new java.util.ArrayList<>();
        List<Decision> viable = new java.util.ArrayList<>();

        for (Offer offer : candidates) {
            Supplier supplier = suppliers.get(offer.getSupplierId());

            if (supplier == null || !"ACTIVE".equals(supplier.getStatus())) {
                rejected.add(new Rejection(offer.getSupplierId(), "supplier is not active"));
                continue;
            }
            if (offer.getAvailableQty() < quantity) {
                rejected.add(new Rejection(supplier.getId(), "cannot cover the quantity"));
                continue;
            }
            if (product.isRequiresColdChain() && !supplier.isColdChain()) {
                rejected.add(new Rejection(supplier.getId(), "no cold chain for a chilled product"));
                continue;
            }
            if (product.isPerishable() && supplier.getLeadTimeHours() >= product.getShelfLifeHours()) {
                rejected.add(
                        new Rejection(
                                supplier.getId(),
                                "lead time of "
                                        + supplier.getLeadTimeHours()
                                        + "h leaves no shelf life against "
                                        + product.getShelfLifeHours()
                                        + "h"));
                continue;
            }

            viable.add(
                    new Decision(
                            offer,
                            supplier,
                            "cheapest supplier that keeps the cold chain and arrives with shelf life left"));
        }

        Optional<Decision> best =
                viable.stream()
                        .min(
                                Comparator.comparingLong((Decision d) -> d.offer().getCostCents())
                                        .thenComparing(
                                                d -> d.supplier().getReliability(),
                                                Comparator.reverseOrder())
                                        .thenComparingInt(d -> d.supplier().getLeadTimeHours()));

        return new Outcome(best, rejected);
    }

    public long marginCents(long listPriceCents, long costCents, int quantity) {
        return (listPriceCents - costCents) * quantity;
    }
}
