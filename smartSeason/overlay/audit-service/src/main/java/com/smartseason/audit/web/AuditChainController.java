package com.smartseason.audit.web;

import com.smartseason.audit.chain.AppendRequest;
import com.smartseason.audit.chain.AuditAppendService;
import com.smartseason.audit.chain.HashChain;
import com.smartseason.audit.web.dto.AuditRecordResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The append-only face of the audit log.
 *
 * Callers post what happened; the service decides where it sits in the chain
 * and what its hash is. The generated CRUD controller still exists for reads,
 * but nothing should write through it.
 */
@RestController
@RequestMapping("/api/audit/v1/audit-trail")
@Tag(name = "Audit trail", description = "Append-only, hash-chained audit records")
public class AuditChainController {

    private final AuditAppendService service;

    public AuditChainController(AuditAppendService service) {
        this.service = service;
    }

    @PostMapping
    @Operation(summary = "Append an entry; sequence and hashes are assigned here")
    public ResponseEntity<AuditRecordResponse> append(@Valid @RequestBody AppendRequest request,
                                                      HttpServletRequest http) {
        AppendRequest enriched = new AppendRequest(
                request.serviceName(), request.actorUserId(), request.actorRole(),
                request.action(), request.resourceType(), request.resourceId(),
                request.outcome(), request.occurredAt(),
                request.ipAddress() == null ? clientIp(http) : request.ipAddress(),
                request.userAgent() == null ? http.getHeader("User-Agent") : request.userAgent(),
                request.details());

        return ResponseEntity.ok(AuditRecordResponse.from(service.append(enriched)));
    }

    @GetMapping("/verify")
    @Operation(summary = "Recompute the whole chain and report the first break")
    public HashChain.Verification verify() {
        return service.verify();
    }

    @GetMapping("/resource/{resourceId}")
    @Operation(summary = "Every entry recorded against one resource, oldest first")
    public List<AuditRecordResponse> forResource(@PathVariable String resourceId) {
        return service.forResource(resourceId).stream().map(AuditRecordResponse::from).toList();
    }

    private static String clientIp(HttpServletRequest http) {
        String forwarded = http.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return http.getRemoteAddr();
    }
}
