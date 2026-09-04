package com.kenyarealestate.pms.controller;

import com.kenyarealestate.pms.dto.*;
import com.kenyarealestate.pms.service.RentInvoiceService;
import com.kenyarealestate.pms.service.RentPaymentService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@Validated
@RestController
@RequestMapping("/api/invoices")
public class InvoiceController {

    private final RentInvoiceService invoices;
    private final RentPaymentService payments;
    private final CallerIdentity caller;

    public InvoiceController(RentInvoiceService invoices, RentPaymentService payments, CallerIdentity caller) {
        this.invoices = invoices;
        this.payments = payments;
        this.caller = caller;
    }

    @Operation(summary = "Rent invoices across the landlord's whole portfolio")
    @GetMapping("/my")
    public ResponseEntity<Page<InvoiceResponse>> mine(
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0")  @Min(0)           int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            HttpServletRequest r) {
        return ResponseEntity.ok(invoices.listForLandlord(caller.userId(r), status, PageRequest.of(page, size)));
    }

    @Operation(summary = "Invoices addressed to the signed-in tenant")
    @GetMapping("/my-tenancy")
    public ResponseEntity<Page<InvoiceResponse>> myTenancy(
            @RequestParam(defaultValue = "0")  @Min(0)           int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            HttpServletRequest r) {
        return ResponseEntity.ok(invoices.listForTenantUser(caller.userId(r), PageRequest.of(page, size)));
    }

    @Operation(summary = "Payments recorded against one invoice")
    @GetMapping("/{id}/payments")
    public ResponseEntity<List<RentPaymentResponse>> paymentsFor(@PathVariable UUID id, HttpServletRequest r) {
        return ResponseEntity.ok(payments.forInvoice(caller.userId(r), id));
    }

    @Operation(summary = "Start an M-Pesa prompt for this invoice",
               description = "Only the tenant on the lease can do this, and only if their record is linked to a SmartRE account. A landlord collecting by cash, bank or paybill records it instead.")
    @PostMapping("/{id}/pay")
    public ResponseEntity<RentPaymentResponse> pay(@PathVariable UUID id,
                                                   @Valid @RequestBody(required = false) PayInvoiceRequest req,
                                                   HttpServletRequest r) {
        PayInvoiceRequest body = req != null ? req : PayInvoiceRequest.builder().build();
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(payments.startStkPush(caller.userId(r), bearerToken(r), id, body));
    }

    @Operation(summary = "Record rent taken outside the app: cash, bank transfer or paybill")
    @PostMapping("/{id}/record-payment")
    public ResponseEntity<RentPaymentResponse> record(@PathVariable UUID id,
                                                      @Valid @RequestBody RecordPaymentRequest req,
                                                      HttpServletRequest r) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(payments.recordManual(caller.userId(r), id, req));
    }

    @Operation(summary = "Write off an invoice that will not be collected")
    @PutMapping("/{id}/write-off")
    public ResponseEntity<InvoiceResponse> writeOff(@PathVariable UUID id, HttpServletRequest r) {
        return ResponseEntity.ok(invoices.writeOff(caller.userId(r), id));
    }

    private String bearerToken(HttpServletRequest request) {
        if (request.getCookies() != null) {
            for (Cookie c : request.getCookies()) {
                if ("sre_token".equals(c.getName()) && StringUtils.hasText(c.getValue())) return c.getValue();
            }
        }
        String h = request.getHeader("Authorization");
        return (StringUtils.hasText(h) && h.startsWith("Bearer ")) ? h.substring(7) : "";
    }
}
