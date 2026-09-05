package com.smartseason.notification.web;

import com.smartseason.notification.platform.PageResponse;
import com.smartseason.notification.service.UssdSessionService;
import com.smartseason.notification.web.dto.UssdSessionCreateRequest;
import com.smartseason.notification.web.dto.UssdSessionResponse;
import com.smartseason.notification.web.dto.UssdSessionUpdateRequest;
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
@RequestMapping("/api/notification/v1/ussd-sessions")
@Tag(name = "UssdSession", description = "SMS, USSD, push, WhatsApp, email; EN/SW templating, delivery tracking, quiet hours")
public class UssdSessionController {

    private final UssdSessionService service;

    public UssdSessionController(UssdSessionService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(summary = "List ussd-sessions for the caller's tenant")
    public PageResponse<UssdSessionResponse> list(@PageableDefault(size = 20) Pageable pageable) {
        return service.list(pageable);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Fetch a single UssdSession by id")
    public UssdSessionResponse get(@PathVariable UUID id) {
        return service.get(id);
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'OPERATOR')")
    @Operation(summary = "Create a UssdSession")
    public ResponseEntity<UssdSessionResponse> create(@Valid @RequestBody UssdSessionCreateRequest request) {
        UssdSessionResponse created = service.create(request);
        return ResponseEntity.created(URI.create("/api/notification/v1/ussd-sessions/" + created.id())).body(created);
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'OPERATOR')")
    @Operation(summary = "Apply a partial update to a UssdSession")
    public UssdSessionResponse update(@PathVariable UUID id, @Valid @RequestBody UssdSessionUpdateRequest request) {
        return service.update(id, request);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    @Operation(summary = "Delete a UssdSession")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
