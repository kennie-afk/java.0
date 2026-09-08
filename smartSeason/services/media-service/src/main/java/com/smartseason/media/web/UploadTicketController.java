package com.smartseason.media.web;

import com.smartseason.media.platform.PageResponse;
import com.smartseason.media.service.UploadTicketService;
import com.smartseason.media.web.dto.UploadTicketCreateRequest;
import com.smartseason.media.web.dto.UploadTicketResponse;
import com.smartseason.media.web.dto.UploadTicketUpdateRequest;
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
@RequestMapping("/api/media/v1/upload-tickets")
@Tag(name = "UploadTicket", description = "Pre-signed uploads, image/video processing, thumbnails, EXIF/GPS extraction")
public class UploadTicketController {

    private final UploadTicketService service;

    public UploadTicketController(UploadTicketService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'FARMER', 'MANAGER', 'AGRONOMIST', 'STOREKEEPER')")
    @Operation(summary = "List upload-tickets for the caller's tenant")
    public PageResponse<UploadTicketResponse> list(@PageableDefault(size = 20) Pageable pageable) {
        return service.list(pageable);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'FARMER', 'MANAGER', 'AGRONOMIST', 'STOREKEEPER')")
    @Operation(summary = "Fetch a single UploadTicket by id")
    public UploadTicketResponse get(@PathVariable UUID id) {
        return service.get(id);
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'FARMER', 'MANAGER', 'AGRONOMIST', 'STOREKEEPER')")
    @Operation(summary = "Create a UploadTicket")
    public ResponseEntity<UploadTicketResponse> create(@Valid @RequestBody UploadTicketCreateRequest request) {
        UploadTicketResponse created = service.create(request);
        return ResponseEntity.created(URI.create("/api/media/v1/upload-tickets/" + created.id())).body(created);
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'FARMER', 'MANAGER', 'AGRONOMIST', 'STOREKEEPER')")
    @Operation(summary = "Apply a partial update to a UploadTicket")
    public UploadTicketResponse update(@PathVariable UUID id, @Valid @RequestBody UploadTicketUpdateRequest request) {
        return service.update(id, request);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN')")
    @Operation(summary = "Delete a UploadTicket")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
