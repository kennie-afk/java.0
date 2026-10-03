package com.mara.identity.credential;

import com.mara.kit.auth.CredentialVerifier;
import com.mara.platform.credential.OperatorToken;
import com.mara.platform.ratelimit.FixedWindowLimiter;
import java.time.Clock;
import java.time.Duration;
import org.springframework.stereotype.Component;

/**
 * identity-service's answer to "may this credential make this request": looked up in its own
 * database, compared in constant time, and checked for revocation, expiry, scope and tenant, in
 * that order. Every refusal is audited (bounded per source address, so a flood of bad credentials
 * cannot grow the audit table without limit); a successful write through the back office is too.
 */
@Component
public class DbCredentialVerifier implements CredentialVerifier {

    private final OperatorCredentialService credentials;
    private final Clock clock;
    private final FixedWindowLimiter denialAudit = new FixedWindowLimiter(60, Duration.ofMinutes(1), 20_000);

    public DbCredentialVerifier(OperatorCredentialService credentials, Clock clock) {
        this.credentials = credentials;
        this.clock = clock;
    }

    @Override
    public Decision verify(Request r) {
        var parsed = OperatorToken.parse(r.presented());
        if (parsed.isEmpty()) {
            return deny(null, null, 401, "malformed", r);
        }
        var row = credentials.lookup(parsed.get().keyId());
        if (row.isEmpty()) {
            return deny(null, parsed.get().keyId(), 401, "unknown_credential", r);
        }
        var c = row.get();
        if (!OperatorToken.matches(parsed.get().secret(), c.secretHash())) {
            return deny(c, parsed.get().keyId(), 401, "bad_secret", r);
        }
        if (c.revokedAt() != null) {
            return deny(c, parsed.get().keyId(), 401, "revoked", r);
        }
        if (!c.expiresAt().isAfter(clock.instant())) {
            return deny(c, parsed.get().keyId(), 401, "expired", r);
        }
        if (!c.scopes().contains(r.requiredScope())) {
            return deny(c, parsed.get().keyId(), 403, "scope_denied", r);
        }
        if (c.tenantId() != null && r.tenantHeader() != null && !c.tenantId().equals(r.tenantHeader())) {
            return deny(c, parsed.get().keyId(), 403, "tenant_mismatch", r);
        }
        credentials.touch(c.id());
        if (r.path().startsWith("/v1/admin") && !"GET".equals(r.method()) && !"HEAD".equals(r.method())) {
            credentials.audit(c.id(), parsed.get().keyId(), "USED_WRITE", r.method() + " tenant=" + r.tenantHeader(),
                    r.remoteAddr(), r.path());
        }
        return Decision.allowed(c.id().toString(), c.label(), c.tenantId());
    }

    private Decision deny(OperatorCredentialService.Row c, String keyId, int status, String reason, Request r) {
        if (denialAudit.tryAcquire(r.remoteAddr() == null ? "?" : r.remoteAddr(), clock.instant())) {
            credentials.audit(c == null ? null : c.id(), keyId, "DENIED", reason + " scope=" + r.requiredScope()
                    + " tenant=" + r.tenantHeader(), r.remoteAddr(), r.method() + " " + r.path());
        }
        return Decision.denied(status, reason);
    }
}
