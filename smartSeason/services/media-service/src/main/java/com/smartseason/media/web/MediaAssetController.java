package com.smartseason.media.web;

import com.smartseason.media.platform.PageResponse;
import com.smartseason.media.service.MediaAssetService;
import com.smartseason.media.web.dto.MediaAssetCreateRequest;
import com.smartseason.media.web.dto.MediaAssetResponse;
import com.smartseason.media.web.dto.MediaAssetUpdateRequest;
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
@RequestMapping("/api/media/v1/media-assets")
@Tag(name = "MediaAsset", description = "Pre-signed uploads, image/video processing, thumbnails, EXIF/GPS extraction")
public class MediaAssetController {

    private final MediaAssetService service;

    public MediaAssetController(MediaAssetService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'FARMER', 'MANAGER', 'AGRONOMIST', 'STOREKEEPER')")
    @Operation(summary = "List media-assets for the caller's tenant")
    public PageResponse<MediaAssetResponse> list(@PageableDefault(size = 20) Pageable pageable) {
        return service.list(pageable);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'FARMER', 'MANAGER', 'AGRONOMIST', 'STOREKEEPER')")
    @Operation(summary = "Fetch a single MediaAsset by id")
    public MediaAssetResponse get(@PathVariable UUID id) {
        return service.get(id);
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'FARMER', 'MANAGER', 'AGRONOMIST', 'STOREKEEPER')")
    @Operation(summary = "Create a MediaAsset")
    public ResponseEntity<MediaAssetResponse> create(@Valid @RequestBody MediaAssetCreateRequest request) {
        MediaAssetResponse created = service.create(request);
        return ResponseEntity.created(URI.create("/api/media/v1/media-assets/" + created.id())).body(created);
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'FARMER', 'MANAGER', 'AGRONOMIST', 'STOREKEEPER')")
    @Operation(summary = "Apply a partial update to a MediaAsset")
    public MediaAssetResponse update(@PathVariable UUID id, @Valid @RequestBody MediaAssetUpdateRequest request) {
        return service.update(id, request);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN')")
    @Operation(summary = "Delete a MediaAsset")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
