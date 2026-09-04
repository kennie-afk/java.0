package com.kenyarealestate.pms.controller;

import com.kenyarealestate.pms.dto.RentInvoiceRefResponse;
import com.kenyarealestate.pms.service.RentInvoiceService;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/pms/internal")
public class InternalPmsController {

    private final RentInvoiceService invoices;

    public InternalPmsController(RentInvoiceService invoices) {
        this.invoices = invoices;
    }

    @Operation(summary = "Internal: resolve a paybill account number to a rent invoice",
               description = "Called by payment-service when Safaricom asks whether an M-Pesa C2B account number is one of ours. Requires the internal-secret header.")
    @GetMapping("/invoices/by-reference/{reference}")
    public ResponseEntity<RentInvoiceRefResponse> byReference(@PathVariable String reference) {
        return ResponseEntity.ok(invoices.resolveByReference(reference));
    }
}
