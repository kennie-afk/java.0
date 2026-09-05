package com.smartseason.attendance.web;

import com.smartseason.attendance.platform.PageResponse;
import com.smartseason.attendance.service.PieceRateEntryService;
import com.smartseason.attendance.web.dto.PieceRateEntryCreateRequest;
import com.smartseason.attendance.web.dto.PieceRateEntryResponse;
import com.smartseason.attendance.web.dto.PieceRateEntryUpdateRequest;
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
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/attendance/v1/piece-rate-entries")
@Tag(name = "PieceRateEntry", description = "Geofenced biometric clock-in/out, shifts, piece-rate tallies, offline sync")
public class PieceRateEntryController {

    private final PieceRateEntryService service;

    public PieceRateEntryController(PieceRateEntryService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(summary = "List piece-rate-entries for the caller's tenant")
    public PageResponse<PieceRateEntryResponse> list(@PageableDefault(size = 20) Pageable pageable) {
        return service.list(pageable);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Fetch a single PieceRateEntry by id")
    public PieceRateEntryResponse get(@PathVariable UUID id) {
        return service.get(id);
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'OPERATOR')")
    @Operation(summary = "Create a PieceRateEntry")
    public ResponseEntity<PieceRateEntryResponse> create(@Valid @RequestBody PieceRateEntryCreateRequest request) {
        PieceRateEntryResponse created = service.create(request);
        return ResponseEntity.created(URI.create("/api/attendance/v1/piece-rate-entries/" + created.id())).body(created);
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'OPERATOR')")
    @Operation(summary = "Apply a partial update to a PieceRateEntry")
    public PieceRateEntryResponse update(@PathVariable UUID id, @Valid @RequestBody PieceRateEntryUpdateRequest request) {
        return service.update(id, request);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    @Operation(summary = "Delete a PieceRateEntry")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
