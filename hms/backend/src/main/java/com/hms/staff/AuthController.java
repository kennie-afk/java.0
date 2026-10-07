package com.hms.staff;

import com.hms.platform.audit.AuditService;
import com.hms.platform.rbac.AccessService;
import com.hms.platform.security.JwtService;
import com.hms.platform.tenancy.TenantContext;
import com.hms.platform.web.ApiException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/auth")
class AuthController {

    record LoginRequest(@NotBlank @Email String email, @NotBlank String password) {}

    record FacilityRef(UUID id, String name) {}

    record Session(String token, long expiresInSeconds, String refreshToken, long refreshExpiresInSeconds, UUID practitionerId, String fullName,
                   UUID organisationId, List<FacilityRef> facilities, List<String> permissions) {}

    record RefreshRequest(@NotBlank @Size(max = 200) String refreshToken) {}

    /** {@code refreshToken} is absent when the same refresh token was used a moment ago by a parallel request: keep the one you have. */
    record Refreshed(String token, long expiresInSeconds, String refreshToken, long refreshExpiresInSeconds) {}

    private static final int LOCK_AFTER = 5;
    private static final int LOCK_MINUTES = 15;

    private final JdbcClient jdbc;
    private final PasswordEncoder encoder;
    private final JwtService jwt;
    private final AccessService access;
    private final TransactionTemplate tx;
    private final SessionService sessions;
    private final AuditService audit;
    /** Compared against when the email is unknown, so an unknown email costs the same time as a wrong password. */
    private final String dummyHash;

    AuthController(JdbcClient jdbc, PasswordEncoder encoder, JwtService jwt, AccessService access, PlatformTransactionManager txm,
                   SessionService sessions, AuditService audit) {
        this.sessions = sessions;
        this.audit = audit;
        this.tx = new TransactionTemplate(txm);
        this.tx.setReadOnly(true);
        this.jdbc = jdbc;
        this.encoder = encoder;
        this.jwt = jwt;
        this.access = access;
        this.dummyHash = encoder.encode("not-a-real-password");
    }

    private record Account(UUID id, UUID orgId, String hash, String fullName, String status, int failed,
                           OffsetDateTime lockedUntil, String orgStatus) {}

    // Deliberately NOT one big transaction: the lookup and the lockout bookkeeping are owner-rights
    // functions that need no tenant, while everything after them must run in a transaction stamped
    // with the person's organisation, which only exists once they are authenticated.
    @PostMapping("/login")
    Session login(@Valid @RequestBody LoginRequest in) {
        Account account = jdbc.sql("SELECT * FROM login_lookup(?::citext)").param(in.email().trim())
                .query((rs, n) -> new Account(rs.getObject("id", UUID.class), rs.getObject("org_id", UUID.class),
                        rs.getString("password_hash"), rs.getString("full_name"), rs.getString("status"),
                        rs.getInt("failed_logins"), rs.getObject("locked_until", OffsetDateTime.class), rs.getString("org_status")))
                .optional().orElse(null);
        boolean matches = encoder.matches(in.password(), account == null ? dummyHash : account.hash());
        if (account == null || !matches) {
            if (account != null) {
                jdbc.sql("SELECT record_login_result(?, false, ?, ?)").params(account.id(), LOCK_AFTER, LOCK_MINUTES).query().singleRow();
            }
            throw new ApiException(HttpStatus.UNAUTHORIZED, "invalid_credentials", "Email or password is not right.");
        }
        if (account.lockedUntil() != null && account.lockedUntil().isAfter(OffsetDateTime.now())) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "account_locked", "Too many failed attempts. Try again later.");
        }
        if (!"ACTIVE".equals(account.status()) || !"ACTIVE".equals(account.orgStatus())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "account_disabled", "This account is not active.");
        }
        jdbc.sql("SELECT record_login_result(?, true, ?, ?)").params(account.id(), LOCK_AFTER, LOCK_MINUTES).query().singleRow();
        return session(account.orgId(), account.id(), account.fullName());
    }

    private Session session(UUID orgId, UUID practitionerId, String fullName) {
        AccessService.Access granted = access.resolve(orgId, practitionerId);
        TenantContext.Tenant previous = TenantContext.orNull();
        TenantContext.set(new TenantContext.Tenant(orgId, practitionerId, granted.facilityIds(), granted.permissions()));
        try {
            List<FacilityRef> facilities = tx.execute(status -> facilitiesOf(granted.facilityIds()));
            SessionService.Issued issued = sessions.start(orgId, practitionerId);
            return new Session(issued.accessToken(), issued.expiresInSeconds(), issued.refreshToken(), issued.refreshExpiresInSeconds(), practitionerId, fullName, orgId,
                    facilities, granted.permissions().stream().sorted().toList());
        } finally {
            if (previous == null) {
                TenantContext.clear();
            } else {
                TenantContext.set(previous);
            }
        }
    }

    private List<FacilityRef> facilitiesOf(Set<UUID> ids) {
        if (ids.isEmpty()) {
            return List.of();
        }
        // Reading under the caller's tenant: row-level security restricts this to their organisation.
        return jdbc.sql("SELECT id, name FROM facilities WHERE id = ANY (?) ORDER BY name").param(ids.toArray(UUID[]::new))
                .query((rs, n) -> new FacilityRef(rs.getObject("id", UUID.class), rs.getString("name"))).list().stream()
                .sorted(Comparator.comparing(FacilityRef::name)).toList();
    }

    /**
     * Exchanges a refresh token for a new access token and the next refresh token. A refresh token works once: presenting a spent one
     * again revokes the whole session family, and the person must sign in.
     */
    @PostMapping("/refresh")
    Refreshed refresh(@Valid @RequestBody RefreshRequest in) {
        SessionService.Rotated r = sessions.rotate(in.refreshToken());
        if (r.outcome().equals("REUSED")) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "refresh_reused", "This session was used twice and has been ended. Sign in again.");
        }
        if (r.tokens() == null) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "invalid_refresh", "Sign in again.");
        }
        SessionService.Issued t = r.tokens();
        return new Refreshed(t.accessToken(), t.expiresInSeconds(), t.refreshToken(), t.refreshExpiresInSeconds());
    }

    /** Signs this device out. Works without a valid access token (it may have expired), and says nothing about whether the token existed. */
    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void logout(@Valid @RequestBody RefreshRequest in) {
        sessions.revokeFamily(in.refreshToken());
    }

    /** Ends every session of the caller, on every device. */
    @PostMapping("/logout-all")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Transactional
    void logoutAll() {
        TenantContext.Tenant t = TenantContext.require();
        sessions.revokeAll(t.orgId(), t.practitionerId());
        audit.record("auth.logout_all", "practitioner", t.practitionerId(), null, null, java.util.Map.of());
    }

    record Me(UUID practitionerId, UUID organisationId, List<FacilityRef> facilities, List<String> permissions) {}

    @GetMapping("/me")
    @Transactional(readOnly = true)
    Me me() {
        TenantContext.Tenant t = TenantContext.require();
        return new Me(t.practitionerId(), t.orgId(), facilitiesOf(t.facilityIds()), t.permissions().stream().sorted().toList());
    }
}
