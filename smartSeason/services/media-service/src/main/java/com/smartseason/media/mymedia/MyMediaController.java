package com.smartseason.media.mymedia;

import com.smartseason.media.web.dto.MediaAssetResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/media/v1/my-media")
@Tag(name = "My media", description = "Media uploaded by the signed-in account")
public class MyMediaController {

    private final MyMediaService service;

    public MyMediaController(MyMediaService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'WORKER')")
    @Operation(summary = "Media uploaded by the signed-in account")
    public List<MediaAssetResponse> mine(Authentication authentication) {
        return service.mine(callerId(authentication)).stream()
                .map(MediaAssetResponse::from).toList();
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'WORKER')")
    @Operation(summary = "One of the signed-in account's own media assets")
    public MediaAssetResponse one(@PathVariable UUID id, Authentication authentication) {
        return MediaAssetResponse.from(service.one(id, callerId(authentication)));
    }

    private static UUID callerId(Authentication authentication) {
        return UUID.fromString(authentication.getName());
    }
}
