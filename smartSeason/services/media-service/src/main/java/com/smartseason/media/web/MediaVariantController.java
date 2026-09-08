package com.smartseason.media.web;

import com.smartseason.media.platform.PageResponse;
import com.smartseason.media.service.MediaVariantService;
import com.smartseason.media.web.dto.MediaVariantCreateRequest;
import com.smartseason.media.web.dto.MediaVariantResponse;
import com.smartseason.media.web.dto.MediaVariantUpdateRequest;
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
@RequestMapping("/api/media/v1/media-variants")
@Tag(name = "MediaVariant", description = "Pre-signed uploads, image/video processing, thumbnails, EXIF/GPS extraction")
public class MediaVariantController {

    private final MediaVariantService service;

    public MediaVariantController(MediaVariantService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'FARMER', 'MANAGER', 'AGRONOMIST', 'STOREKEEPER')")
    @Operation(summary = "List media-variants for the caller's tenant")
    public PageResponse<MediaVariantResponse> list(@PageableDefault(size = 20) Pageable pageable) {
        return service.list(pageable);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'FARMER', 'MANAGER', 'AGRONOMIST', 'STOREKEEPER')")
    @Operation(summary = "Fetch a single MediaVariant by id")
    public MediaVariantResponse get(@PathVariable UUID id) {
        return service.get(id);
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'FARMER', 'MANAGER', 'AGRONOMIST', 'STOREKEEPER')")
    @Operation(summary = "Create a MediaVariant")
    public ResponseEntity<MediaVariantResponse> create(@Valid @RequestBody MediaVariantCreateRequest request) {
        MediaVariantResponse created = service.create(request);
        return ResponseEntity.created(URI.create("/api/media/v1/media-variants/" + created.id())).body(created);
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'FARMER', 'MANAGER', 'AGRONOMIST', 'STOREKEEPER')")
    @Operation(summary = "Apply a partial update to a MediaVariant")
    public MediaVariantResponse update(@PathVariable UUID id, @Valid @RequestBody MediaVariantUpdateRequest request) {
        return service.update(id, request);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN')")
    @Operation(summary = "Delete a MediaVariant")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
