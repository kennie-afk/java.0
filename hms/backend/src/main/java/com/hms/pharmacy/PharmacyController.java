package com.hms.pharmacy;

import static com.hms.pharmacy.PharmacyModels.*;

import com.hms.platform.rbac.Permissions;
import com.hms.platform.web.Slice;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/v1/pharmacy")
class PharmacyController {

    private static final String READ = "hasAuthority('" + Permissions.PHARMACY_READ + "')";
    private static final String STOCK = "hasAuthority('" + Permissions.PHARMACY_STOCK + "')";
    private static final String DISPENSE = "hasAuthority('" + Permissions.PHARMACY_DISPENSE + "')";

    private final PharmacyService pharmacy;

    PharmacyController(PharmacyService pharmacy) {
        this.pharmacy = pharmacy;
    }

    @GetMapping("/drugs")
    @PreAuthorize(READ)
    Slice<Drug> drugs(@RequestParam(required = false) String q, @RequestParam(defaultValue = "true") boolean activeOnly,
                      @RequestParam(required = false) String cursor, @RequestParam(required = false) Integer limit) {
        return pharmacy.drugs(q, activeOnly, cursor, limit);
    }

    @PostMapping("/drugs")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(STOCK)
    Drug createDrug(@Valid @RequestBody DrugInput in) {
        return pharmacy.createDrug(in);
    }

    @PutMapping("/drugs/{id}")
    @PreAuthorize(STOCK)
    Drug updateDrug(@PathVariable UUID id, @Valid @RequestBody DrugInput in) {
        return pharmacy.updateDrug(id, in);
    }

    @GetMapping("/stock")
    @PreAuthorize(READ)
    Slice<StockLine> stock(@RequestParam UUID facilityId, @RequestParam(required = false) String q, @RequestParam(defaultValue = "false") boolean belowReorder,
                           @RequestParam(required = false) String cursor, @RequestParam(required = false) Integer limit) {
        return pharmacy.stock(facilityId, q, belowReorder, cursor, limit);
    }

    @GetMapping("/stock/batches")
    @PreAuthorize(READ)
    List<Batch> batches(@RequestParam UUID facilityId, @RequestParam(required = false) UUID drugId, @RequestParam(required = false) Integer expiringWithinDays) {
        return pharmacy.batches(facilityId, drugId, expiringWithinDays);
    }

    @PostMapping("/stock/receipts")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(STOCK)
    Batch receive(@Valid @RequestBody Receipt in) {
        return pharmacy.receive(in);
    }

    @PostMapping("/stock/adjustments")
    @PreAuthorize(STOCK)
    Batch adjust(@Valid @RequestBody Adjustment in) {
        return pharmacy.adjust(in);
    }

    @GetMapping("/stock/movements")
    @PreAuthorize(READ)
    Slice<Movement> movements(@RequestParam UUID facilityId, @RequestParam(required = false) UUID drugId, @RequestParam(defaultValue = "false") boolean controlledOnly,
                              @RequestParam(required = false) String cursor, @RequestParam(required = false) Integer limit) {
        return pharmacy.movements(facilityId, drugId, controlledOnly, cursor, limit);
    }

    @GetMapping("/queue")
    @PreAuthorize(READ)
    Slice<PendingOrder> queue(@RequestParam UUID facilityId, @RequestParam(required = false) String cursor, @RequestParam(required = false) Integer limit) {
        return pharmacy.queue(facilityId, cursor, limit);
    }

    @PostMapping("/dispense")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(DISPENSE)
    Dispensing dispense(@Valid @RequestBody DispenseInput in) {
        return pharmacy.dispense(in);
    }
}
