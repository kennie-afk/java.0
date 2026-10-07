package com.hms.staff;

import com.hms.platform.rbac.AccessService;
import com.hms.platform.security.JwtService;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

/**
 * Staff sessions. An access token is a short signed JWT; a refresh token is an opaque random string, kept only as its SHA-256, usable once.
 * Using one hands back the next in the same family. Presenting a spent one outside the grace window revokes the whole family (two parties
 * hold it). Ending a person's sessions (password change or reset, "sign out everywhere") also moves practitioners.tokens_valid_after, which
 * the token filter checks through the cached access lookup, so already-issued access tokens die within the cache window (HMS_ROLE_CACHE_TTL_MS,
 * 15 s; immediately on the replica that handled the change). All timestamps come from the database clock, so replicas cannot disagree.
 */
@Service
public class SessionService {

    public record Issued(String accessToken, long expiresInSeconds, String refreshToken, long refreshExpiresInSeconds) {}

    /** {@code refreshToken} is null for GRACE: the caller keeps the refresh token it already has (see V22). */
    public record Rotated(String outcome, UUID orgId, UUID practitionerId, Issued tokens) {}

    private static final Pattern SHAPE = Pattern.compile("^r1\\.[A-Za-z0-9_-]{43}$");
    private static final SecureRandom RANDOM = new SecureRandom();

    private final JdbcClient jdbc;
    private final JwtService jwt;
    private final AccessService access;
    private final long refreshTtlSeconds;
    private final long familyMaxSeconds;
    private final int graceSeconds;

    SessionService(JdbcClient jdbc, JwtService jwt, AccessService access,
                   @Value("${hms.jwt.refresh-ttl-hours:168}") long refreshTtlHours, @Value("${hms.jwt.refresh-family-max-days:30}") long familyMaxDays,
                   @Value("${hms.jwt.refresh-grace-seconds:10}") int graceSeconds) {
        this.jdbc = jdbc;
        this.jwt = jwt;
        this.access = access;
        this.refreshTtlSeconds = refreshTtlHours * 3600;
        this.familyMaxSeconds = familyMaxDays * 86_400;
        this.graceSeconds = graceSeconds;
    }

    /** A new session (a new refresh family) for a person who has just proved who they are. */
    Issued start(UUID orgId, UUID practitionerId) {
        String refresh = newRefreshToken();
        jdbc.sql("SELECT refresh_issue(?, ?, ?, ?)").params(orgId, practitionerId, hash(refresh), (int) refreshTtlSeconds).query().singleRow();
        return new Issued(accessToken(orgId, practitionerId), jwt.ttlSeconds(), refresh, refreshTtlSeconds);
    }

    /** Exchanges a refresh token. Outcomes: OK, GRACE, REUSED, INVALID. */
    Rotated rotate(String presented) {
        if (presented == null || !SHAPE.matcher(presented).matches()) {
            return new Rotated("INVALID", null, null, null);
        }
        String next = newRefreshToken();
        var row = jdbc.sql("SELECT * FROM refresh_rotate(?, ?, ?, ?, ?)").params(hash(presented), hash(next), (int) refreshTtlSeconds, (int) familyMaxSeconds, graceSeconds)
                .query((rs, n) -> new Object[] {rs.getString("outcome"), rs.getObject("org_id", UUID.class), rs.getObject("practitioner_id", UUID.class)}).single();
        String outcome = (String) row[0];
        UUID org = (UUID) row[1];
        UUID practitioner = (UUID) row[2];
        if (!outcome.equals("OK") && !outcome.equals("GRACE")) {
            return new Rotated(outcome, null, null, null);
        }
        // The roles cache may still hold the previous state: refresh also proves the person is still active.
        if (!access.resolve(org, practitioner).active()) {
            return new Rotated("INVALID", null, null, null);
        }
        Issued tokens = new Issued(accessToken(org, practitioner), jwt.ttlSeconds(), outcome.equals("OK") ? next : null, refreshTtlSeconds);
        return new Rotated(outcome, org, practitioner, tokens);
    }

    /** Signs one device out. Unknown tokens are ignored. */
    void revokeFamily(String presented) {
        if (presented != null && SHAPE.matcher(presented).matches()) {
            jdbc.sql("SELECT refresh_revoke_family(?)").param(hash(presented)).query().singleRow();
        }
    }

    /** Ends every session of one person, here and on every device. Safe inside a tenant transaction. */
    public void revokeAll(UUID orgId, UUID practitionerId) {
        jdbc.sql("SELECT sessions_revoke_all(?, ?)").params(orgId, practitionerId).query().singleRow();
        access.invalidateAll();
    }

    private String accessToken(UUID orgId, UUID practitionerId) {
        long now = jdbc.sql("SELECT db_clock_millis()").query(Long.class).single();
        return jwt.issue(practitionerId, orgId, now);
    }

    private static String newRefreshToken() {
        byte[] b = new byte[32];
        RANDOM.nextBytes(b);
        return "r1." + Base64.getUrlEncoder().withoutPadding().encodeToString(b);
    }

    private static byte[] hash(String token) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
