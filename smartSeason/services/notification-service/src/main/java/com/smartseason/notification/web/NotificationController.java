package com.smartseason.notification.web;

import com.smartseason.notification.platform.PageResponse;
import com.smartseason.notification.service.NotificationService;
import com.smartseason.notification.web.dto.NotificationCreateRequest;
import com.smartseason.notification.web.dto.NotificationResponse;
import com.smartseason.notification.web.dto.NotificationUpdateRequest;
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
@RequestMapping("/api/notification/v1/notifications")
@Tag(name = "Notification", description = "SMS, USSD, push, WhatsApp, email; EN/SW templating, delivery tracking, quiet hours")
public class NotificationController {

    private final NotificationService service;

    public NotificationController(NotificationService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'FARMER')")
    @Operation(summary = "List notifications for the caller's tenant")
    public PageResponse<NotificationResponse> list(@PageableDefault(size = 20) Pageable pageable) {
        return service.list(pageable);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'FARMER')")
    @Operation(summary = "Fetch a single Notification by id")
    public NotificationResponse get(@PathVariable UUID id) {
        return service.get(id);
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN')")
    @Operation(summary = "Create a Notification")
    public ResponseEntity<NotificationResponse> create(@Valid @RequestBody NotificationCreateRequest request) {
        NotificationResponse created = service.create(request);
        return ResponseEntity.created(URI.create("/api/notification/v1/notifications/" + created.id())).body(created);
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN')")
    @Operation(summary = "Apply a partial update to a Notification")
    public NotificationResponse update(@PathVariable UUID id, @Valid @RequestBody NotificationUpdateRequest request) {
        return service.update(id, request);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN')")
    @Operation(summary = "Delete a Notification")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
