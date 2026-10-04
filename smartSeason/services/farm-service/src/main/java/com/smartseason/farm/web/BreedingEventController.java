package com.smartseason.farm.web;

import com.smartseason.farm.platform.CursorPage;
import com.smartseason.farm.platform.PageResponse;
import com.smartseason.farm.service.BreedingEventService;
import com.smartseason.farm.web.dto.BreedingEventCreateRequest;
import com.smartseason.farm.web.dto.BreedingEventResponse;
import com.smartseason.farm.web.dto.BreedingEventUpdateRequest;
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
@RequestMapping("/api/farm/v1/breeding-events")
@Tag(name = "BreedingEvent", description = "Farms, plots, geo boundaries, soil profiles, cooperative membership")
public class BreedingEventController {

    private final BreedingEventService service;

    public BreedingEventController(BreedingEventService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'FARMER', 'MANAGER', 'AGRONOMIST')")
    @Operation(summary = "List breeding-events for the caller's tenant")
    public PageResponse<BreedingEventResponse> list(@PageableDefault(size = 20) Pageable pageable,
                                             @RequestParam java.util.Map<String, String> params) {
        return service.list(pageable, params);
    }

    @GetMapping("/cursor")
    @PreAuthorize("hasAnyRole('ADMIN', 'FARMER', 'MANAGER', 'AGRONOMIST')")
    @Operation(summary = "List breeding-events from a cursor, newest first, without an offset scan")
    public CursorPage<BreedingEventResponse> listByCursor(
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "25") int size) {
        return service.listByCursor(cursor, size);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'FARMER', 'MANAGER', 'AGRONOMIST')")
    @Operation(summary = "Fetch a single BreedingEvent by id")
    public BreedingEventResponse get(@PathVariable UUID id) {
        return service.get(id);
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'FARMER', 'MANAGER')")
    @Operation(summary = "Create a BreedingEvent")
    public ResponseEntity<BreedingEventResponse> create(@Valid @RequestBody BreedingEventCreateRequest request) {
        BreedingEventResponse created = service.create(request);
        return ResponseEntity.created(URI.create("/api/farm/v1/breeding-events/" + created.id())).body(created);
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'FARMER', 'MANAGER')")
    @Operation(summary = "Apply a partial update to a BreedingEvent")
    public BreedingEventResponse update(@PathVariable UUID id, @Valid @RequestBody BreedingEventUpdateRequest request) {
        return service.update(id, request);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'FARMER')")
    @Operation(summary = "Delete a BreedingEvent")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
