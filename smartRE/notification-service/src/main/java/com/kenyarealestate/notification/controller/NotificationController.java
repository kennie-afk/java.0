package com.kenyarealestate.notification.controller;

import com.kenyarealestate.notification.dto.*;
import com.kenyarealestate.notification.entity.NotificationStatus;
import com.kenyarealestate.notification.exception.ForbiddenException;
import com.kenyarealestate.notification.security.JwtUtil;
import com.kenyarealestate.notification.service.DispatchCommand;
import com.kenyarealestate.notification.service.NotificationDispatcher;
import com.kenyarealestate.notification.service.NotificationService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Validated
@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

    private final NotificationService svc;
    private final NotificationDispatcher dispatcher;
    private final JwtUtil jwtUtil;

    public NotificationController(NotificationService svc,
                                  NotificationDispatcher dispatcher,
                                  JwtUtil jwtUtil) {
        this.svc = svc;
        this.dispatcher = dispatcher;
        this.jwtUtil = jwtUtil;
    }

    @Operation(summary = "The signed-in user's in-app notifications, newest first")
    @GetMapping("/my")
    public ResponseEntity<Page<NotificationResponse>> feed(
            @RequestParam(defaultValue = "0")  @Min(0)           int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            HttpServletRequest request) {
        return ResponseEntity.ok(svc.feed(resolveUserId(request), PageRequest.of(page, size)));
    }

    @Operation(summary = "Unread count for the notification bell")
    @GetMapping("/my/unread-count")
    public ResponseEntity<UnreadCountResponse> unreadCount(HttpServletRequest request) {
        return ResponseEntity.ok(svc.unreadCount(resolveUserId(request)));
    }

    @Operation(summary = "Mark one notification read")
    @PutMapping("/{id}/read")
    public ResponseEntity<NotificationResponse> markRead(@PathVariable UUID id, HttpServletRequest request) {
        return ResponseEntity.ok(svc.markRead(resolveUserId(request), id));
    }

    @Operation(summary = "Mark every unread notification read")
    @PutMapping("/my/read-all")
    public ResponseEntity<Map<String, Integer>> markAllRead(HttpServletRequest request) {
        return ResponseEntity.ok(Map.of("updated", svc.markAllRead(resolveUserId(request))));
    }

    @Operation(summary = "Notification preferences for every category")
    @GetMapping("/my/preferences")
    public ResponseEntity<List<PreferenceResponse>> preferences(HttpServletRequest request) {
        return ResponseEntity.ok(svc.getPreferences(resolveUserId(request)));
    }

    @Operation(summary = "Update one category's notification preferences")
    @PutMapping("/my/preferences")
    public ResponseEntity<PreferenceResponse> updatePreferences(
            @Valid @RequestBody UpdatePreferenceRequest req, HttpServletRequest request) {
        return ResponseEntity.ok(svc.updatePreference(resolveUserId(request), req));
    }

    @Operation(summary = "Internal: send a notification not derived from a domain event",
               description = "Called by other services (password reset today). Requires the "
                           + "internal-secret header; not reachable from outside the docker network.")
    @PostMapping("/internal/send")
    public ResponseEntity<Void> internalSend(@Valid @RequestBody InternalSendRequest req) {
        dispatcher.dispatch(DispatchCommand.builder()
                .userId(req.getUserId())
                .templateCode(req.getTemplateCode())
                .recipientEmail(req.getRecipientEmail())
                .dedupKeyOverride(req.getDedupKey())
                .sourceEventType("INTERNAL_SEND")
                .sourceEventId(req.getDedupKey())
                .entityType(req.getEntityType())
                .entityId(req.getEntityId())
                .actionUrl(req.getActionUrl())
                .model(req.getModel())
                .build());
        return ResponseEntity.accepted().build();
    }

    @Operation(summary = "Admin: the delivery log, optionally filtered by status")
    @GetMapping("/admin")
    public ResponseEntity<Page<AdminNotificationResponse>> adminList(
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0")  @Min(0)           int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        NotificationStatus parsed = null;
        if (StringUtils.hasText(status)) {
            try {
                parsed = NotificationStatus.valueOf(status.toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException(
                        "Unknown status '" + status + "'. Use one of PENDING, SENT, FAILED, SUPPRESSED.");
            }
        }
        return ResponseEntity.ok(svc.adminList(parsed, PageRequest.of(page, size)));
    }

    @Operation(summary = "Admin: retry a notification that gave up")
    @PostMapping("/admin/{id}/retry")
    public ResponseEntity<AdminNotificationResponse> retry(@PathVariable UUID id) {
        return ResponseEntity.status(HttpStatus.OK).body(svc.retry(id));
    }

    private UUID resolveUserId(HttpServletRequest request) {
        Object attr = request.getAttribute("authenticatedUserId");
        if (attr instanceof UUID uuid) return uuid;

        String token = extractToken(request);
        if (StringUtils.hasText(token) && jwtUtil.isValid(token)) {
            UUID uid = jwtUtil.extractUserId(token);
            if (uid != null) return uid;
        }
        throw new ForbiddenException("Unable to identify the signed-in user");
    }

    private String extractToken(HttpServletRequest request) {
        if (request.getCookies() != null) {
            for (Cookie c : request.getCookies()) {
                if ("sre_token".equals(c.getName()) && StringUtils.hasText(c.getValue())) return c.getValue();
            }
        }
        String h = request.getHeader("Authorization");
        return (StringUtils.hasText(h) && h.startsWith("Bearer ")) ? h.substring(7) : null;
    }
}
