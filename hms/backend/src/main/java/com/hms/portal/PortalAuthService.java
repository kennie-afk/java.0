package com.hms.portal;

import static com.hms.portal.PortalModels.*;

import com.hms.platform.security.JwtService;
import com.hms.platform.tenancy.TenantContext;
import com.hms.platform.web.ApiException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.OffsetDateTime;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Patient sign-in. Mirrors staff login: the lookup and the lockout bookkeeping are owner-rights functions that need no
 * tenant, an unknown login costs the same time as a wrong password, and five failures lock the account for 15 minutes.
 */
@Service
public class PortalAuthService {

    private static final int LOCK_AFTER = 5;
    private static final int LOCK_MINUTES = 15;
    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
    private static final Pattern PHONE = Pattern.compile("^\\+?[0-9]{9,15}$");

    private final JdbcClient jdbc;
    private final PasswordEncoder encoder;
    private final JwtService jwt;
    private final TransactionTemplate readTx;
    private final String dummyHash;

    public PortalAuthService(JdbcClient jdbc, PasswordEncoder encoder, JwtService jwt, PlatformTransactionManager txm) {
        this.jdbc = jdbc;
        this.encoder = encoder;
        this.jwt = jwt;
        this.readTx = new TransactionTemplate(txm);
        this.readTx.setReadOnly(true);
        this.dummyHash = encoder.encode("not-a-real-password");
    }

    /** A sign-in name is an e-mail address or a phone number, normalised so the same person always types the same thing. */
    static String normaliseLogin(String raw) {
        String v = raw.trim().toLowerCase(Locale.ROOT);
        if (EMAIL.matcher(v).matches()) {
            return v;
        }
        String phone = v.replaceAll("[\\s-]", "");
        if (PHONE.matcher(phone).matches()) {
            return phone;
        }
        throw ApiException.badRequest("login_format", "Use an e-mail address or a phone number to sign in.");
    }

    static byte[] hashCode(String code) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(code.trim().toUpperCase(Locale.ROOT).replace("-", "").replace(" ", "").getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    public void activate(ActivateInput in) {
        String login = normaliseLogin(in.login());
        if (in.password().equalsIgnoreCase(login)) {
            throw ApiException.badRequest("password_weak", "The password cannot be the same as the sign-in name.");
        }
        String result = jdbc.sql("SELECT portal_activate(?::citext, ?, ?, ?::citext, ?)")
                .params(in.organisation().trim(), hashCode(in.code()), in.birthDate(), login, encoder.encode(in.password())).query(String.class).single();
        switch (result) {
            case "ok" -> { }
            case "account_exists" -> throw ApiException.conflict("account_exists", "This patient already has a portal account.");
            case "login_taken" -> throw ApiException.conflict("login_taken", "That sign-in name is already used. Choose another.");
            default -> throw ApiException.badRequest("invalid_invitation", "The organisation, code or date of birth is not right, or the invitation has expired.");
        }
    }

    private record Account(UUID id, UUID orgId, UUID patientId, String hash, String status, int failed, OffsetDateTime lockedUntil, String orgStatus) {}

    public Session login(LoginInput in) {
        String login;
        try {
            login = normaliseLogin(in.login());
        } catch (ApiException e) {
            encoder.matches(in.password(), dummyHash);
            throw new ApiException(HttpStatus.UNAUTHORIZED, "invalid_credentials", "Sign-in name or password is not right.");
        }
        Account account = jdbc.sql("SELECT * FROM portal_login_lookup(?::citext, ?::citext)").params(in.organisation().trim(), login)
                .query((rs, n) -> new Account(rs.getObject("id", UUID.class), rs.getObject("org_id", UUID.class), rs.getObject("patient_id", UUID.class), rs.getString("password_hash"),
                        rs.getString("status"), rs.getInt("failed_logins"), rs.getObject("locked_until", OffsetDateTime.class), rs.getString("org_status"))).optional().orElse(null);
        boolean matches = encoder.matches(in.password(), account == null ? dummyHash : account.hash());
        if (account == null || !matches) {
            if (account != null) {
                jdbc.sql("SELECT portal_record_login_result(?, false, ?, ?)").params(account.id(), LOCK_AFTER, LOCK_MINUTES).query().singleRow();
            }
            throw new ApiException(HttpStatus.UNAUTHORIZED, "invalid_credentials", "Sign-in name or password is not right.");
        }
        if (account.lockedUntil() != null && account.lockedUntil().isAfter(OffsetDateTime.now())) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "account_locked", "Too many failed attempts. Try again later.");
        }
        if (!"ACTIVE".equals(account.status()) || !"ACTIVE".equals(account.orgStatus())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "account_disabled", "This account is not active. Ask the facility.");
        }
        jdbc.sql("SELECT portal_record_login_result(?, true, ?, ?)").params(account.id(), LOCK_AFTER, LOCK_MINUTES).query().singleRow();
        // The name and organisation are read under the patient's own tenant, so row-level security applies.
        TenantContext.set(new TenantContext.Tenant(account.orgId(), account.id(), java.util.Set.of(), java.util.Set.of()));
        try {
            String[] names = readTx.execute(s -> new String[] {
                    jdbc.sql("SELECT given_name || ' ' || family_name FROM patients WHERE org_id = ? AND id = ?").params(account.orgId(), account.patientId()).query(String.class).single(),
                    jdbc.sql("SELECT name FROM organisations WHERE id = ?").param(account.orgId()).query(String.class).single()});
            return new Session(jwt.issuePortal(account.id(), account.orgId(), account.patientId()), Math.min(jwt.ttlSeconds(), 30 * 60L), names[0], names[1]);
        } finally {
            TenantContext.clear();
        }
    }
}
