package com.smartseason.farm.web;

import com.smartseason.farm.platform.CursorPage;
import com.smartseason.farm.platform.PageResponse;
import com.smartseason.farm.service.MilkYieldService;
import com.smartseason.farm.web.dto.MilkYieldCreateRequest;
import com.smartseason.farm.web.dto.MilkYieldResponse;
import com.smartseason.farm.web.dto.MilkYieldUpdateRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/farm/v1/milk-yields")
@Tag(name = "MilkYield", description = "Farms, plots, geo boundaries, soil profiles, cooperative membership")
public class MilkYieldController {

    private final MilkYieldService service;

    public MilkYieldController(MilkYieldService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'FARMER', 'MANAGER', 'AGRONOMIST')")
    @Operation(summary = "List milk-yields for the caller's tenant")
    public PageResponse<MilkYieldResponse> list(@PageableDefault(size = 20) Pageable pageable,
                                             @RequestParam java.util.Map<String, String> params) {
        return service.list(pageable, params);
    }

    @GetMapping("/cursor")
    @PreAuthorize("hasAnyRole('ADMIN', 'FARMER', 'MANAGER', 'AGRONOMIST')")
    @Operation(summary = "List milk-yields from a cursor, newest first, without an offset scan")
    public CursorPage<MilkYieldResponse> listByCursor(
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "25") int size) {
        return service.listByCursor(cursor, size);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'FARMER', 'MANAGER', 'AGRONOMIST')")
    @Operation(summary = "Fetch a single MilkYield by id")
    public MilkYieldResponse get(@PathVariable UUID id) {
        return service.get(id);
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'FARMER', 'MANAGER')")
    @Operation(summary = "Create a MilkYield")
    public ResponseEntity<MilkYieldResponse> create(@Valid @RequestBody MilkYieldCreateRequest request) {
        MilkYieldResponse created = service.create(request);
        return ResponseEntity.created(URI.create("/api/farm/v1/milk-yields/" + created.id())).body(created);
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'FARMER', 'MANAGER')")
    @Operation(summary = "Apply a partial update to a MilkYield")
    public MilkYieldResponse update(@PathVariable UUID id, @Valid @RequestBody MilkYieldUpdateRequest request) {
        return service.update(id, request);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'FARMER')")
    @Operation(summary = "Delete a MilkYield")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
