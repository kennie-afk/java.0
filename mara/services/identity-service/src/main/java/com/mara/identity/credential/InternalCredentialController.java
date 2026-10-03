package com.mara.identity.credential;

import com.mara.kit.auth.CredentialVerifier;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.HashMap;
import java.util.Map;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * Lets sync-service and core-service ask whether a credential someone presented to THEM may make
 * a request. Always answers 200 with the decision, so a caller can tell "that credential is not
 * valid" from "identity-service is unavailable". Needs a service credential with
 * {@code credentials:verify}.
 */
@RestController
public class InternalCredentialController {

    private final CredentialVerifier verifier;

    public InternalCredentialController(CredentialVerifier verifier) {
        this.verifier = verifier;
    }

    public record Verify(@NotBlank String credential, @NotBlank String requiredScope, String tenant, String method,
                         String path, String remoteAddr) {
    }

    @PostMapping("/v1/internal/credentials/verify")
    public Map<String, Object> verify(@Valid @RequestBody Verify r) {
        var d = verifier.verify(new CredentialVerifier.Request(r.credential(), r.requiredScope(),
                r.tenant() == null || r.tenant().isBlank() ? null : r.tenant(),
                r.method() == null ? "GET" : r.method(), r.path() == null ? "" : r.path(), r.remoteAddr()));
        Map<String, Object> out = new HashMap<>();
        out.put("ok", d.ok());
        out.put("status", d.status());
        out.put("reason", d.reason());
        out.put("credentialId", d.credentialId());
        out.put("label", d.label());
        out.put("tenantId", d.tenantId());
        return out;
    }
}
