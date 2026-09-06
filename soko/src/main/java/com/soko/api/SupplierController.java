package com.soko.api;

import com.soko.domain.Offer;
import com.soko.domain.OrderLine;
import com.soko.domain.Supplier;
import com.soko.persistence.OfferRepository;
import com.soko.persistence.OrderLineRepository;
import com.soko.persistence.SupplierRepository;
import com.soko.platform.Errors;
import com.soko.security.Principal;
import com.soko.security.TenantContext;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/v1/supplier")
public class SupplierController {

    private final SupplierRepository suppliers;
    private final OfferRepository offers;
    private final OrderLineRepository orderLines;
    private final TenantContext context;

    public SupplierController(
            SupplierRepository suppliers,
            OfferRepository offers,
            OrderLineRepository orderLines,
            TenantContext context) {
        this.suppliers = suppliers;
        this.offers = offers;
        this.orderLines = orderLines;
        this.context = context;
    }

    private UUID supplierId() {
        Principal principal = context.current();
        if (principal.supplierId() == null) {
            throw new Errors.Unauthorized("this endpoint is for supplier accounts");
        }
        return principal.supplierId();
    }

    @GetMapping("/profile")
    public Supplier profile() {
        return suppliers
                .findById(supplierId())
                .orElseThrow(() -> new Errors.NotFound("no such supplier"));
    }

    @GetMapping("/offers")
    public List<Map<String, Object>> myOffers() {
        return offers.offersForSupplier(supplierId()).stream()
                .map(r -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("id", r[0]);
                    row.put("product", r[1]);
                    row.put("sku", r[2]);
                    row.put("unit", r[3]);
                    row.put("category", r[4]);
                    row.put("costCents", r[5]);
                    row.put("availableQty", r[6]);
                    row.put("status", r[7]);
                    row.put("listPriceCents", r[8]);
                    row.put("chilled", r[9]);
                    row.put("shelfLifeHours", r[10]);
                    return row;
                })
                .toList();
    }

    public record OfferUpdate(@Min(1) long costCents, @Min(0) int availableQty) {}

    @PutMapping("/offers/{id}")
    public Offer updateOffer(@PathVariable UUID id, @Valid @RequestBody OfferUpdate request) {
        Offer offer =
                offers.findByIdAndSupplierId(id, supplierId())
                        .orElseThrow(() -> new Errors.NotFound("no such offer"));
        offer.setCostCents(request.costCents());
        offer.setAvailableQty(request.availableQty());
        return offers.save(offer);
    }

    @GetMapping("/fulfilments")
    public List<Map<String, Object>> fulfilments(@RequestParam(defaultValue = "100") int limit) {
        return orderLines.fulfilmentsForSupplier(supplierId(), Math.min(limit, 200)).stream()
                .map(r -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("lineId", r[0]);
                    row.put("product", r[1]);
                    row.put("reference", r[2]);
                    row.put("customer", r[3]);
                    row.put("county", r[4]);
                    row.put("quantity", r[5]);
                    row.put("unitPayoutCents", r[6]);
                    row.put("status", r[7]);
                    row.put("placedAt", r[8]);
                    row.put("trackingNote", r[9]);
                    return row;
                })
                .toList();
    }

    public record Dispatch(@Size(max = 200) String trackingNote) {}

    @PostMapping("/fulfilments/{lineId}/dispatch")
    public Map<String, Object> dispatch(
            @PathVariable UUID lineId, @Valid @RequestBody Dispatch request) {
        OrderLine line =
                orderLines
                        .findByIdAndSupplierId(lineId, supplierId())
                        .orElseThrow(() -> new Errors.NotFound("no such fulfilment"));
        if (!"ROUTED".equals(line.getStatus())) {
            throw new Errors.BadRequest("that line is already " + line.getStatus().toLowerCase());
        }
        line.setStatus("DISPATCHED");
        line.setDispatchedAt(Instant.now());
        line.setTrackingNote(request.trackingNote());
        orderLines.save(line);
        return Map.of("lineId", line.getId(), "status", line.getStatus());
    }

    @PostMapping("/fulfilments/{lineId}/deliver")
    public Map<String, Object> deliver(@PathVariable UUID lineId) {
        OrderLine line =
                orderLines
                        .findByIdAndSupplierId(lineId, supplierId())
                        .orElseThrow(() -> new Errors.NotFound("no such fulfilment"));
        if (!"DISPATCHED".equals(line.getStatus())) {
            throw new Errors.BadRequest("that line has not been dispatched yet");
        }
        line.setStatus("DELIVERED");
        line.setDeliveredAt(Instant.now());
        orderLines.save(line);
        return Map.of("lineId", line.getId(), "status", line.getStatus());
    }
}
