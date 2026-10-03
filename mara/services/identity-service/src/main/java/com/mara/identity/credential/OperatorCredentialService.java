package com.mara.identity.credential;

import com.mara.platform.credential.OperatorToken;
import com.mara.platform.credential.Scopes;
import java.sql.Array;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * Issues, rotates, revokes and looks up operator and service credentials. Every database access
 * goes through a SECURITY DEFINER function (the application role has no privilege on the tables),
 * and the secret is only ever returned from {@link #issue} and {@link #rotate}, once.
 */
@Service
public class OperatorCredentialService {

    public static final Duration MIN_LIFE = Duration.ofMinutes(5);
    public static final Duration MAX_OPERATOR_LIFE = Duration.ofHours(720);
    public static final Duration DEFAULT_OPERATOR_LIFE = Duration.ofHours(12);

    public static class Refused extends RuntimeException {
        public Refused(String message) {
            super(message);
        }
    }

    /** What the server knows about a credential, found by its public key id. Never exposes the secret. */
    public record Row(UUID id, byte[] secretHash, String label, String kind, String tenantId, List<String> scopes,
                      Instant expiresAt, Instant revokedAt) {
    }

    /** The one time a secret is shown. */
    public record Issued(UUID id, String keyId, String token, Instant expiresAt) {
    }

    private final JdbcTemplate jdbc;
    private final Clock clock;

    public OperatorCredentialService(JdbcTemplate jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    private static List<String> texts(Array a) throws java.sql.SQLException {
        return a == null ? List.of() : Arrays.asList((String[]) a.getArray());
    }

    public Optional<Row> lookup(String keyId) {
        return jdbc.query("SELECT * FROM credential_lookup(?)", (rs, i) -> new Row(
                        rs.getObject("id", UUID.class), rs.getBytes("secret_hash"), rs.getString("label"), rs.getString("kind"),
                        rs.getString("tenant_id"), texts(rs.getArray("scopes")),
                        rs.getTimestamp("expires_at").toInstant(),
                        rs.getTimestamp("revoked_at") == null ? null : rs.getTimestamp("revoked_at").toInstant()), keyId)
                .stream().findFirst();
    }

    public void touch(UUID id) {
        jdbc.queryForList("SELECT credential_touch(?)", id);
    }

    public Issued issue(String label, String tenantId, List<String> scopes, Duration life, String createdBy) {
        if (label == null || label.isBlank() || label.length() > 80) {
            throw new Refused("label is required (80 characters at most)");
        }
        String problem = Scopes.problem("OPERATOR", tenantId, scopes);
        if (problem != null) {
            throw new Refused(problem);
        }
        Duration effective = life == null ? DEFAULT_OPERATOR_LIFE : life;
        if (effective.compareTo(MIN_LIFE) < 0 || effective.compareTo(MAX_OPERATOR_LIFE) > 0) {
            throw new Refused("an operator credential lives between 5 minutes and 720 hours");
        }
        return mint(label.trim(), "OPERATOR", tenantId, scopes, effective, createdBy, "ISSUED");
    }

    private Issued mint(String label, String kind, String tenantId, List<String> scopes, Duration life, String createdBy,
                        String action) {
        OperatorToken.Minted m = OperatorToken.mint();
        Instant expires = clock.instant().plus(life);
        UUID id = jdbc.execute((java.sql.Connection c) -> {
            try (var ps = c.prepareStatement("SELECT credential_issue(?, ?, ?, ?, ?, ?, ?, ?)")) {
                ps.setString(1, m.keyId());
                ps.setBytes(2, m.secretHash());
                ps.setString(3, label);
                ps.setString(4, kind);
                ps.setString(5, tenantId);
                ps.setArray(6, c.createArrayOf("text", scopes.toArray()));
                ps.setString(7, createdBy);
                ps.setTimestamp(8, Timestamp.from(expires));
                try (var rs = ps.executeQuery()) {
                    rs.next();
                    return rs.getObject(1, UUID.class);
                }
            }
        });
        audit(id, m.keyId(), action, "label=" + label + " tenant=" + tenantId + " scopes=" + scopes + " by=" + createdBy,
                null, null);
        return new Issued(id, m.keyId(), m.token(), expires);
    }

    public boolean revoke(UUID id, String by) {
        Boolean done = jdbc.queryForObject("SELECT credential_revoke(?)", Boolean.class, id);
        if (Boolean.TRUE.equals(done)) {
            audit(id, null, "REVOKED", "by=" + by, null, null);
        }
        return Boolean.TRUE.equals(done);
    }

    /**
     * Replaces a credential with a new one of the same label, tenant and scopes and the same
     * lifetime, and lets the old one run on for a short grace so a rollout can switch over.
     */
    public Issued rotate(UUID id, Duration grace, String by) {
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT * FROM credential_list(500)");
        Map<String, Object> old = rows.stream().filter(r -> id.equals(r.get("id"))).findFirst()
                .orElseThrow(() -> new Refused("no such credential"));
        if (!"OPERATOR".equals(old.get("kind"))) {
            throw new Refused("service credentials are rotated through the environment, not here");
        }
        if (old.get("revoked_at") != null) {
            throw new Refused("that credential is already revoked");
        }
        Duration life = Duration.between(((Timestamp) old.get("created_at")).toInstant(),
                ((Timestamp) old.get("expires_at")).toInstant());
        life = life.compareTo(MIN_LIFE) < 0 ? DEFAULT_OPERATOR_LIFE : life.compareTo(MAX_OPERATOR_LIFE) > 0 ? MAX_OPERATOR_LIFE : life;
        try {
            @SuppressWarnings("unchecked")
            List<String> scopes = texts((Array) old.get("scopes"));
            Issued next = mint((String) old.get("label"), "OPERATOR", (String) old.get("tenant_id"), scopes, life, by,
                    "ROTATED");
            jdbc.queryForList("SELECT credential_expire_by(?, ?)", id, Timestamp.from(clock.instant().plus(grace)));
            return next;
        } catch (java.sql.SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    public List<Map<String, Object>> list(int limit) {
        return jdbc.queryForList("SELECT id, key_id, label, kind, tenant_id, scopes::text AS scopes, created_at, created_by, "
                + "expires_at, revoked_at, last_used_at FROM credential_list(?)", limit);
    }

    public List<Map<String, Object>> auditTrail(int limit) {
        return jdbc.queryForList("SELECT * FROM credential_audit_read(?)", limit);
    }

    public void audit(UUID credentialId, String keyId, String action, String detail, String addr, String path) {
        jdbc.queryForList("SELECT credential_audit_write(?, ?, ?, ?, ?, ?)", credentialId, keyId, action, detail, addr, path);
    }

    /** Startup registration of a deployer-minted credential; see {@code credential_seed}. */
    public int seed(String label, String kind, List<String> scopes, OperatorToken.Parsed token, List<String> keep,
                    Instant expires) {
        return jdbc.execute((java.sql.Connection c) -> {
            try (var ps = c.prepareStatement("SELECT credential_seed(?, ?, ?, ?, ?, ?, ?)")) {
                ps.setString(1, label);
                ps.setString(2, kind);
                ps.setArray(3, c.createArrayOf("text", scopes.toArray()));
                ps.setString(4, token == null ? null : token.keyId());
                ps.setBytes(5, token == null ? null : OperatorToken.hash(token.secret()));
                ps.setArray(6, c.createArrayOf("text", keep.toArray()));
                ps.setTimestamp(7, Timestamp.from(expires));
                try (var rs = ps.executeQuery()) {
                    rs.next();
                    return rs.getInt(1);
                }
            }
        });
    }
}
