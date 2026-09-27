package com.soko.api;

import com.soko.domain.Tenant;
import com.soko.persistence.OfferRepository;
import com.soko.persistence.TenantRepository;
import com.soko.platform.Errors;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The one storefront view that needs no account at all: a public product
 * catalogue, resolved by tenant slug rather than by an authenticated
 * principal's tenant. This is deliberately unauthenticated (see
 * SecurityConfig) - a price list is exactly what a real shop publishes
 * openly, and this returns nothing beyond what {@code /v1/shop/products}
 * already shows a signed-in customer: no supplier name, no cost, no margin.
 */
@RestController
@RequestMapping("/v1/public")
public class PublicController {

    private final TenantRepository tenants;
    private final OfferRepository offers;

    public PublicController(TenantRepository tenants, OfferRepository offers) {
        this.tenants = tenants;
        this.offers = offers;
    }

    @GetMapping("/{slug}/products")
    public List<Map<String, Object>> catalogue(@PathVariable String slug) {
        Tenant tenant = tenants.findBySlug(slug).orElseThrow(() -> new Errors.NotFound("no such storefront"));

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
}
