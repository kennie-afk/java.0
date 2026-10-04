package com.smartseason.farm.web;

import com.smartseason.farm.platform.CursorPage;
import com.smartseason.farm.platform.PageResponse;
import com.smartseason.farm.service.CowService;
import com.smartseason.farm.web.dto.CowCreateRequest;
import com.smartseason.farm.web.dto.CowResponse;
import com.smartseason.farm.web.dto.CowUpdateRequest;
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
@RequestMapping("/api/farm/v1/cows")
@Tag(name = "Cow", description = "Farms, plots, geo boundaries, soil profiles, cooperative membership")
public class CowController {

    private final CowService service;

    public CowController(CowService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'FARMER', 'MANAGER', 'AGRONOMIST')")
    @Operation(summary = "List cows for the caller's tenant")
    public PageResponse<CowResponse> list(@PageableDefault(size = 20) Pageable pageable,
                                             @RequestParam java.util.Map<String, String> params) {
        return service.list(pageable, params);
    }

    @GetMapping("/cursor")
    @PreAuthorize("hasAnyRole('ADMIN', 'FARMER', 'MANAGER', 'AGRONOMIST')")
    @Operation(summary = "List cows from a cursor, newest first, without an offset scan")
    public CursorPage<CowResponse> listByCursor(
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "25") int size) {
        return service.listByCursor(cursor, size);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'FARMER', 'MANAGER', 'AGRONOMIST')")
    @Operation(summary = "Fetch a single Cow by id")
    public CowResponse get(@PathVariable UUID id) {
        return service.get(id);
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'FARMER', 'MANAGER')")
    @Operation(summary = "Create a Cow")
    public ResponseEntity<CowResponse> create(@Valid @RequestBody CowCreateRequest request) {
        CowResponse created = service.create(request);
        return ResponseEntity.created(URI.create("/api/farm/v1/cows/" + created.id())).body(created);
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'FARMER', 'MANAGER')")
    @Operation(summary = "Apply a partial update to a Cow")
    public CowResponse update(@PathVariable UUID id, @Valid @RequestBody CowUpdateRequest request) {
        return service.update(id, request);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'FARMER')")
    @Operation(summary = "Delete a Cow")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
