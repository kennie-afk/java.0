package com.kenyarealestate.pms.controller;

import com.kenyarealestate.pms.dto.*;
import com.kenyarealestate.pms.service.LeaseService;
import com.kenyarealestate.pms.service.UnitService;
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

import java.util.List;
import java.util.UUID;

@Validated
@RestController
@RequestMapping("/api/units")
public class UnitController {

    private final UnitService units;
    private final LeaseService leases;
    private final CallerIdentity caller;

    public UnitController(UnitService units, LeaseService leases, CallerIdentity caller) {
        this.units = units;
        this.leases = leases;
        this.caller = caller;
    }

    @Operation(summary = "Add a unit to a property you own")
    @PostMapping
    public ResponseEntity<UnitResponse> create(@Valid @RequestBody CreateUnitRequest req, HttpServletRequest r) {
        return ResponseEntity.status(HttpStatus.CREATED).body(units.create(caller.userId(r), req));
    }

    @Operation(summary = "Your units, newest first")
    @GetMapping("/my")
    public ResponseEntity<Page<UnitResponse>> mine(
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0")  @Min(0)           int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            HttpServletRequest r) {
        return ResponseEntity.ok(units.listMine(caller.userId(r), status, PageRequest.of(page, size)));
    }

    @Operation(summary = "Portfolio totals for the signed-in landlord")
    @GetMapping("/my/summary")
    public ResponseEntity<PortfolioSummaryResponse> summary(HttpServletRequest r) {
        return ResponseEntity.ok(units.summary(caller.userId(r)));
    }

    @Operation(summary = "Units on one of your properties")
    @GetMapping("/property/{propertyId}")
    public ResponseEntity<List<UnitResponse>> byProperty(@PathVariable UUID propertyId, HttpServletRequest r) {
        return ResponseEntity.ok(units.listByProperty(caller.userId(r), propertyId));
    }

    @GetMapping("/{id}")
    public ResponseEntity<UnitResponse> get(@PathVariable UUID id, HttpServletRequest r) {
        return ResponseEntity.ok(units.get(caller.userId(r), id));
    }

    @Operation(summary = "Every lease this unit has ever had")
    @GetMapping("/{id}/leases")
    public ResponseEntity<List<LeaseResponse>> history(@PathVariable UUID id, HttpServletRequest r) {
        return ResponseEntity.ok(leases.historyForUnit(caller.userId(r), id));
    }

    @PutMapping("/{id}")
    public ResponseEntity<UnitResponse> update(@PathVariable UUID id,
                                               @Valid @RequestBody UpdateUnitRequest req,
                                               HttpServletRequest r) {
        return ResponseEntity.ok(units.update(caller.userId(r), id, req));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id, HttpServletRequest r) {
        units.delete(caller.userId(r), id);
        return ResponseEntity.noContent().build();
    }
}
