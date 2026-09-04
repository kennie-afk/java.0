package com.kenyarealestate.pms.controller;

import com.kenyarealestate.pms.dto.*;
import com.kenyarealestate.pms.service.TenantService;
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

import java.util.Map;
import java.util.UUID;

@Validated
@RestController
@RequestMapping("/api/tenants")
public class TenantController {

    private final TenantService tenants;
    private final CallerIdentity caller;

    public TenantController(TenantService tenants, CallerIdentity caller) {
        this.tenants = tenants;
        this.caller = caller;
    }

    @Operation(summary = "Record a tenant. They do not need a SmartRE account.")
    @PostMapping
    public ResponseEntity<TenantResponse> create(@Valid @RequestBody CreateTenantRequest req, HttpServletRequest r) {
        return ResponseEntity.status(HttpStatus.CREATED).body(tenants.create(caller.userId(r), req));
    }

    @GetMapping("/my")
    public ResponseEntity<Page<TenantResponse>> mine(
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "0")  @Min(0)           int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            HttpServletRequest r) {
        return ResponseEntity.ok(tenants.listMine(caller.userId(r), q, PageRequest.of(page, size)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<TenantResponse> get(@PathVariable UUID id, HttpServletRequest r) {
        return ResponseEntity.ok(tenants.get(caller.userId(r), id));
    }

    @PutMapping("/{id}")
    public ResponseEntity<TenantResponse> update(@PathVariable UUID id,
                                                 @Valid @RequestBody UpdateTenantRequest req,
                                                 HttpServletRequest r) {
        return ResponseEntity.ok(tenants.update(caller.userId(r), id, req));
    }

    @Operation(summary = "Link this tenant record to a registered SmartRE account")
    @PutMapping("/{id}/link-user")
    public ResponseEntity<TenantResponse> linkUser(@PathVariable UUID id,
                                                   @RequestBody Map<String, String> body,
                                                   HttpServletRequest r) {
        String raw = body.get("userId");
        if (raw == null) throw new IllegalArgumentException("userId is required");
        return ResponseEntity.ok(tenants.linkUser(caller.userId(r), id, UUID.fromString(raw)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id, HttpServletRequest r) {
        tenants.delete(caller.userId(r), id);
        return ResponseEntity.noContent().build();
    }
}
