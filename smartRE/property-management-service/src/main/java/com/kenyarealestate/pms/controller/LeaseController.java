package com.kenyarealestate.pms.controller;

import com.kenyarealestate.pms.dto.*;
import com.kenyarealestate.pms.service.LeaseService;
import com.kenyarealestate.pms.service.RentInvoiceService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@Validated
@RestController
@RequestMapping("/api/leases")
public class LeaseController {

    private final LeaseService leases;
    private final RentInvoiceService invoices;
    private final CallerIdentity caller;

    public LeaseController(LeaseService leases, RentInvoiceService invoices, CallerIdentity caller) {
        this.leases = leases;
        this.invoices = invoices;
        this.caller = caller;
    }

    @Operation(summary = "Draft a lease. It does not take effect until activated.")
    @PostMapping
    public ResponseEntity<LeaseResponse> create(@Valid @RequestBody CreateLeaseRequest req, HttpServletRequest r) {
        return ResponseEntity.status(HttpStatus.CREATED).body(leases.create(caller.userId(r), req));
    }

    @GetMapping("/my")
    public ResponseEntity<Page<LeaseResponse>> mine(
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0")  @Min(0)           int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            HttpServletRequest r) {
        return ResponseEntity.ok(leases.listMine(caller.userId(r), status, PageRequest.of(page, size)));
    }

    @Operation(summary = "Leases where the signed-in user is the tenant")
    @GetMapping("/my-tenancy")
    public ResponseEntity<Page<LeaseResponse>> myTenancy(
            @RequestParam(defaultValue = "0")  @Min(0)           int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            HttpServletRequest r) {
        return ResponseEntity.ok(leases.listForTenantUser(caller.userId(r), PageRequest.of(page, size)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<LeaseResponse> get(@PathVariable UUID id, HttpServletRequest r) {
        return ResponseEntity.ok(leases.get(caller.userId(r), id));
    }

    @Operation(summary = "Every rent invoice raised against this lease")
    @GetMapping("/{id}/invoices")
    public ResponseEntity<java.util.List<com.kenyarealestate.pms.dto.InvoiceResponse>> invoices(
            @PathVariable UUID id, HttpServletRequest r) {
        return ResponseEntity.ok(invoices.listForLease(caller.userId(r), id));
    }

    @Operation(summary = "Bring a draft lease into effect and mark its unit occupied")
    @PutMapping("/{id}/activate")
    public ResponseEntity<LeaseResponse> activate(@PathVariable UUID id, HttpServletRequest r) {
        return ResponseEntity.ok(leases.activate(caller.userId(r), id));
    }

    @Operation(summary = "End a lease that has run its course")
    @PutMapping("/{id}/end")
    public ResponseEntity<LeaseResponse> end(@PathVariable UUID id, HttpServletRequest r) {
        return ResponseEntity.ok(leases.end(caller.userId(r), id));
    }

    @Operation(summary = "Terminate a lease early, with a reason")
    @PutMapping("/{id}/terminate")
    public ResponseEntity<LeaseResponse> terminate(@PathVariable UUID id,
                                                   @Valid @RequestBody TerminateLeaseRequest req,
                                                   HttpServletRequest r) {
        return ResponseEntity.ok(leases.terminate(caller.userId(r), id, req.getReason()));
    }

    @Operation(summary = "Draft a follow-on lease for the same unit and tenant")
    @PostMapping("/{id}/renew")
    public ResponseEntity<LeaseResponse> renew(@PathVariable UUID id,
                                               @Valid @RequestBody RenewLeaseRequest req,
                                               HttpServletRequest r) {
        return ResponseEntity.status(HttpStatus.CREATED).body(leases.renew(caller.userId(r), id, req));
    }
}
