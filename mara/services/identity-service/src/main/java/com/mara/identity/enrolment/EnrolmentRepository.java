package com.mara.identity.enrolment;

import com.mara.platform.identity.EnrolmentPolicy.PendingEnrolment;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Reads and redeems enrolment codes through the two SECURITY DEFINER functions.
 *
 * <p>Neither call goes through an ordinary query, because enrolment happens before the
 * device has a tenant and every RLS policy would therefore match nothing. Routing it
 * through named functions keeps that exception narrow and auditable: there is exactly
 * one way to cross the tenant boundary in this service, it takes a hash rather than a
 * code, and it is the only thing {@code mara_app} is granted EXECUTE on.
 */
@Repository
public class EnrolmentRepository {

    private final JdbcTemplate jdbc;

    public EnrolmentRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Resolves a code hash to its pending enrolment, or empty if no such code exists.
     *
     * <p>Takes a hash the caller already computed. The plaintext never reaches the
     * database, so a query log or a statement trace cannot be mined for live codes.
     */
    public Optional<Resolved> resolve(byte[] codeHash) {
        return jdbc.query(
                "SELECT tenant_id, branch_id, enrolment_id, issued_at, expires_at, "
                        + "redeemed, active_terminals, licensed_terminals "
                        + "FROM resolve_enrolment(?)",
                rs -> {
                    if (!rs.next()) {
                        return Optional.empty();
                    }
                    return Optional.of(new Resolved(
                            rs.getString("tenant_id"),
                            rs.getString("branch_id"),
                            rs.getString("enrolment_id"),
                            rs.getTimestamp("issued_at").toInstant(),
                            rs.getTimestamp("expires_at").toInstant(),
                            rs.getBoolean("redeemed"),
                            rs.getInt("active_terminals"),
                            rs.getInt("licensed_terminals")));
                },
                codeHash);
    }

    /**
     * Marks the code spent, and reports whether this caller was the one who spent it.
     *
     * <p>The function is a single conditional UPDATE, so two devices presenting the same
     * code concurrently both arrive here and exactly one sees true. There is no lock to
     * hold and no read-then-write window to lose.
     */
    public boolean redeem(String enrolmentId, String terminalId, Instant now) {
        Boolean claimed = jdbc.queryForObject(
                "SELECT redeem_enrolment(?, ?, ?)",
                Boolean.class,
                enrolmentId, terminalId, Timestamp.from(now));
        return Boolean.TRUE.equals(claimed);
    }

    /**
     * Binds the resolved tenant for the remainder of this transaction.
     *
     * <p>Enrolment begins with no tenant context — the device is nobody until its code
     * resolves — but the terminal row it then writes is tenant-scoped, and row-level
     * security will reject an insert whose {@code tenant_id} does not match the current
     * tenant. So the moment the tenant is known, the transaction adopts it.
     *
     * <p>Adopting rather than exempting matters: the insert is then checked by the same
     * policy as every other write, against a tenant the server derived from the code,
     * not one the caller asserted.
     */
    public void bindTenant(String tenantId) {
        jdbc.query("SELECT set_config('mara.tenant_id', ?, true)", rs -> null, tenantId);
    }

    /**
     * True when this public key is already registered to some terminal, in any tenant.
     *
     * <p>Goes through a SECURITY DEFINER function because the answer must span tenants.
     * A cloned device is most interesting precisely when the key belongs to a different
     * shop, and a tenant-scoped query would report "not found" and enrol the clone.
     */
    public boolean isKeyRegistered(String publicKeyBase64) {
        Boolean exists = jdbc.queryForObject(
                "SELECT terminal_key_exists(?)", Boolean.class, publicKeyBase64);
        return Boolean.TRUE.equals(exists);
    }

    public void insertPendingTerminal(
            String terminalId, String tenantId, String branchId, String label,
            String publicKeyBase64, Instant enrolledAt) {
        jdbc.update(
                "INSERT INTO terminal (id, tenant_id, branch_id, label, public_key, status, enrolled_at) "
                        + "VALUES (?, ?, ?, ?, ?, 'ACTIVE', ?)",
                terminalId, tenantId, branchId, label, publicKeyBase64, Timestamp.from(enrolledAt));
    }

    /** What {@code resolve_enrolment} returns, before policy has judged it. */
    public record Resolved(
            String tenantId,
            String branchId,
            String enrolmentId,
            Instant issuedAt,
            Instant expiresAt,
            boolean redeemed,
            int activeTerminals,
            int licensedTerminals) {

        /** Adapts to the shape the pure policy expects. */
        public PendingEnrolment toPending(byte[] codeHash) {
            return new PendingEnrolment(
                    tenantId, codeHash, issuedAt, redeemed, activeTerminals, licensedTerminals);
        }
    }
}
