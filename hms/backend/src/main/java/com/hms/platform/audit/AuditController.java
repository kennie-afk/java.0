package com.hms.platform.audit;

import com.hms.platform.rbac.Permissions;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/audit")
class AuditController {

    private final AuditService audit;

    AuditController(AuditService audit) {
        this.audit = audit;
    }

    /** Re-checks every chain of the organisation. A single failure names the chain and the first bad event. */
    @GetMapping("/verify")
    @PreAuthorize("hasAuthority('" + Permissions.AUDIT_READ + "')")
    List<AuditService.Verification> verify() {
        return audit.chainKeys().stream().map(audit::verify).toList();
    }

    @GetMapping("/events")
    @PreAuthorize("hasAuthority('" + Permissions.AUDIT_READ + "')")
    List<AuditService.Event> events(@RequestParam String entityType, @RequestParam String entityId,
                                    @RequestParam(defaultValue = "50") int limit) {
        return audit.forEntity(entityType, entityId, Math.max(1, Math.min(limit, 200)));
    }
}
