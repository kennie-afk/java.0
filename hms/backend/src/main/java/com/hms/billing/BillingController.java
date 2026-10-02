package com.hms.billing;

import static com.hms.billing.BillingModels.*;

import com.hms.platform.rbac.Permissions;
import com.hms.platform.web.Slice;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/v1/billing")
class BillingController {

    private static final String READ = "hasAuthority('" + Permissions.BILLING_READ + "')";
    private static final String POST = "hasAuthority('" + Permissions.BILLING_POST + "')";
    private static final String REFUND = "hasAuthority('" + Permissions.BILLING_REFUND + "')";
    private static final String MANAGE = "hasAuthority('" + Permissions.BILLING_MANAGE + "')";

    private final BillingService billing;

    BillingController(BillingService billing) {
        this.billing = billing;
    }

    @GetMapping("/charges")
    @PreAuthorize(READ)
    List<Charge> charges(@RequestParam(required = false) String q, @RequestParam(defaultValue = "true") boolean activeOnly) {
        return billing.charges(q, activeOnly);
    }

    @PostMapping("/charges")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(MANAGE)
    Charge createCharge(@Valid @RequestBody ChargeInput in) {
        return billing.createCharge(in);
    }

    @PutMapping("/charges/{id}")
    @PreAuthorize(MANAGE)
    Charge updateCharge(@PathVariable UUID id, @Valid @RequestBody ChargeInput in) {
        return billing.updateCharge(id, in);
    }

    @PostMapping("/invoices")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(POST)
    Invoice create(@Valid @RequestBody InvoiceInput in) {
        return billing.create(in);
    }

    @GetMapping("/invoices")
    @PreAuthorize(READ)
    Slice<InvoiceRow> list(@RequestParam(required = false) UUID facilityId, @RequestParam(required = false) UUID patientId, @RequestParam(required = false) String status,
                           @RequestParam(required = false) String cursor, @RequestParam(required = false) Integer limit) {
        return billing.list(facilityId, patientId, status, cursor, limit);
    }

    @GetMapping("/invoices/{id}")
    @PreAuthorize(READ)
    Invoice open(@PathVariable UUID id) {
        return billing.open(id);
    }

    @PostMapping("/invoices/{id}/lines")
    @PreAuthorize(POST)
    Invoice addLine(@PathVariable UUID id, @Valid @RequestBody LineInput in) {
        return billing.addLine(id, in);
    }

    @DeleteMapping("/invoices/{id}/lines/{lineId}")
    @PreAuthorize(POST)
    Invoice removeLine(@PathVariable UUID id, @PathVariable UUID lineId) {
        return billing.removeLine(id, lineId);
    }

    @PostMapping("/invoices/{id}/import-encounter")
    @PreAuthorize(POST)
    Invoice importEncounter(@PathVariable UUID id) {
        return billing.importEncounter(id);
    }

    @PostMapping("/invoices/{id}/issue")
    @PreAuthorize(POST)
    Invoice issue(@PathVariable UUID id) {
        return billing.issue(id);
    }

    @PostMapping("/invoices/{id}/void")
    @PreAuthorize(REFUND)
    Invoice voidInvoice(@PathVariable UUID id, @Valid @RequestBody VoidInput in) {
        return billing.voidInvoice(id, in);
    }

    @PostMapping("/invoices/{id}/payments")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(POST)
    Payment pay(@PathVariable UUID id, @Valid @RequestBody PaymentInput in) {
        return billing.pay(id, in);
    }

    @PostMapping("/invoices/{id}/mpesa")
    @ResponseStatus(HttpStatus.ACCEPTED)
    @PreAuthorize(POST)
    StkResponse stk(@PathVariable UUID id, @Valid @RequestBody StkInput in) {
        return billing.stkPush(id, in);
    }

    @PostMapping("/mpesa/mock/complete")
    @PreAuthorize(POST)
    Payment completeMock(@Valid @RequestBody MockCompletion in) {
        return billing.completeMock(in);
    }

    @PostMapping("/payments/{id}/reverse")
    @PreAuthorize(REFUND)
    Payment reverse(@PathVariable UUID id, @Valid @RequestBody ReverseInput in) {
        return billing.reverse(id, in);
    }
}
