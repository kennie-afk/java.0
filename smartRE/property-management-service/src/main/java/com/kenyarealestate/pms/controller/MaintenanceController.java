package com.kenyarealestate.pms.controller;

import com.kenyarealestate.pms.dto.*;
import com.kenyarealestate.pms.client.DocumentClient;
import com.kenyarealestate.pms.service.MaintenanceService;
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
@RequestMapping("/api/maintenance")
public class MaintenanceController {

    private final MaintenanceService maintenance;
    private final DocumentClient documents;
    private final CallerIdentity caller;

    public MaintenanceController(MaintenanceService maintenance, DocumentClient documents, CallerIdentity caller) {
        this.maintenance = maintenance;
        this.documents = documents;
        this.caller = caller;
    }

    @Operation(summary = "Report a problem with the unit you rent",
               description = "For the tenant. The unit is taken from their active lease, so it cannot be aimed at someone else's home.")
    @PostMapping("/my-tenancy")
    public ResponseEntity<MaintenanceResponse> raiseAsTenant(@Valid @RequestBody RaiseMaintenanceRequest req,
                                                             HttpServletRequest r) {
        return ResponseEntity.status(HttpStatus.CREATED).body(maintenance.raiseAsTenant(caller.userId(r), req));
    }

    @Operation(summary = "Log a job on one of your own units")
    @PostMapping
    public ResponseEntity<MaintenanceResponse> raiseAsLandlord(@Valid @RequestBody RaiseMaintenanceRequest req,
                                                               HttpServletRequest r) {
        return ResponseEntity.status(HttpStatus.CREATED).body(maintenance.raiseAsLandlord(caller.userId(r), req));
    }

    @Operation(summary = "Maintenance across the landlord's portfolio")
    @GetMapping("/my")
    public ResponseEntity<Page<MaintenanceResponse>> mine(
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0")  @Min(0)           int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            HttpServletRequest r) {
        return ResponseEntity.ok(maintenance.listForLandlord(caller.userId(r), status, PageRequest.of(page, size)));
    }

    @Operation(summary = "Requests the signed-in tenant has raised")
    @GetMapping("/my-tenancy")
    public ResponseEntity<Page<MaintenanceResponse>> myTenancy(
            @RequestParam(defaultValue = "0")  @Min(0)           int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            HttpServletRequest r) {
        return ResponseEntity.ok(maintenance.listForTenantUser(caller.userId(r), PageRequest.of(page, size)));
    }

    @Operation(summary = "A photo attached to a request",
               description = "Served through this service rather than linked directly, because the stored file is readable only by whoever uploaded it. The landlord and the tenant on the request both need to see it, and only this service knows who those are.")
    @GetMapping("/{id}/photos/{index}")
    public ResponseEntity<byte[]> photo(@PathVariable UUID id, @PathVariable int index, HttpServletRequest r) {
        String key = maintenance.photoKeyFor(caller.userId(r), id, index);
        DocumentClient.StoredFile file = documents.fetch(key);
        if (file == null) return ResponseEntity.notFound().build();
        return ResponseEntity.ok()
                .contentType(file.contentType())
                .cacheControl(org.springframework.http.CacheControl.maxAge(java.time.Duration.ofHours(1)).cachePrivate())
                .body(file.bytes());
    }

    @Operation(summary = "Move a request along, assign it, or record what it cost")
    @PutMapping("/{id}")
    public ResponseEntity<MaintenanceResponse> update(@PathVariable UUID id,
                                                      @Valid @RequestBody UpdateMaintenanceRequest req,
                                                      HttpServletRequest r) {
        return ResponseEntity.ok(maintenance.update(caller.userId(r), id, req));
    }
}
