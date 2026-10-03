package com.mara.identity.credential;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Credential management. Needs {@code credentials:manage}, which only a platform credential can hold. */
@RestController
@RequestMapping("/v1/admin/credentials")
public class CredentialController {

    private final OperatorCredentialService credentials;

    public CredentialController(OperatorCredentialService credentials) {
        this.credentials = credentials;
    }

    public record Issue(
            @NotBlank @Size(max = 80) String label,
            @Size(max = 60) String tenantId,
            @NotNull @Size(min = 1, max = 8) List<String> scopes,
            @Min(1) @Max(720) Integer expiresInHours) {
    }

    /** The only response that contains the credential. It cannot be fetched again. */
    @PostMapping
    public ResponseEntity<?> issue(@Valid @RequestBody Issue r, HttpServletRequest request) {
        var life = r.expiresInHours() == null ? null : Duration.ofHours(r.expiresInHours());
        var issued = credentials.issue(r.label(), r.tenantId() == null || r.tenantId().isBlank() ? null : r.tenantId().trim(),
                r.scopes(), life, who(request));
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("id", issued.id(), "keyId", issued.keyId(),
                "credential", issued.token(), "expiresAt", issued.expiresAt().toString()));
    }

    @GetMapping
    public List<Map<String, Object>> list(@RequestParam(defaultValue = "100") int limit) {
        return credentials.list(limit);
    }

    public record Target(@NotNull UUID id, @Min(0) @Max(1440) Integer graceMinutes) {
    }

    @PostMapping("/revoke")
    public ResponseEntity<?> revoke(@Valid @RequestBody Target r, HttpServletRequest request) {
        return credentials.revoke(r.id(), who(request))
                ? ResponseEntity.noContent().build()
                : ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "not_found_or_already_revoked"));
    }

    /** A replacement with the same label, tenant, scopes and lifetime; the old one runs on for the grace. */
    @PostMapping("/rotate")
    public ResponseEntity<?> rotate(@Valid @RequestBody Target r, HttpServletRequest request) {
        var next = credentials.rotate(r.id(), Duration.ofMinutes(r.graceMinutes() == null ? 10 : r.graceMinutes()), who(request));
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("id", next.id(), "keyId", next.keyId(),
                "credential", next.token(), "expiresAt", next.expiresAt().toString()));
    }

    @GetMapping("/audit")
    public List<Map<String, Object>> audit(@RequestParam(defaultValue = "100") int limit) {
        return credentials.auditTrail(limit);
    }

    private static String who(HttpServletRequest request) {
        Object label = request.getAttribute("mara.credential.label");
        return label == null ? "unknown" : label.toString();
    }

    @ExceptionHandler(OperatorCredentialService.Refused.class)
    public ResponseEntity<?> refused(OperatorCredentialService.Refused e) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(Map.of("error", "refused", "message", e.getMessage()));
    }
}
