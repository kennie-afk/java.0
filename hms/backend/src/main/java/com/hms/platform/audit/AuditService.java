package com.hms.platform.audit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.hms.platform.tenancy.TenantContext;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * An append-only, hash-chained record of who did what to which record. Each event commits to the
 * previous one (per organisation and facility), so deleting, editing or re-ordering any event is
 * detectable by {@link #verify}. Writes happen in the SAME transaction as the change they describe:
 * if the change rolls back, so does its audit entry, and a change can never commit unaudited.
 */
@Service
public class AuditService {

    public record Verification(String chainKey, boolean intact, long checked, Long brokenAtSeq) {}

    public record Event(long seq, String chainKey, Instant at, UUID actorId, UUID facilityId, String action,
                        String entityType, String entityId, String reason, String detail) {}

    private static final byte[] GENESIS = new byte[] {0};

    private final JdbcClient jdbc;
    private final ObjectMapper canonical;

    public AuditService(JdbcClient jdbc, ObjectMapper mapper) {
        this.jdbc = jdbc;
        // Sorted keys: the same detail always serialises to the same bytes, so the hash is stable.
        this.canonical = mapper.copy().configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void record(String action, String entityType, Object entityId, UUID facilityId, String reason, Map<String, ?> detail) {
        TenantContext.Tenant tenant = TenantContext.require();
        String chainKey = facilityId == null ? "org" : facilityId.toString();
        jdbc.sql("INSERT INTO audit_chain (org_id, chain_key) VALUES (?, ?) ON CONFLICT DO NOTHING")
                .params(tenant.orgId(), chainKey).update();
        // The row lock serialises writers to ONE chain. Per-facility chains keep contention local.
        var head = jdbc.sql("SELECT last_seq, last_hash FROM audit_chain WHERE org_id = ? AND chain_key = ? FOR UPDATE")
                .params(tenant.orgId(), chainKey)
                .query((rs, n) -> Map.entry(rs.getLong("last_seq"), rs.getBytes("last_hash"))).single();
        long seq = head.getKey() + 1;
        byte[] previous = head.getValue();
        // Postgres stores microseconds. Truncating here makes the stored value equal the hashed one.
        Instant at = Instant.now().truncatedTo(ChronoUnit.MICROS);
        String id = entityId == null ? null : entityId.toString();
        String detailJson = write(detail == null ? Map.of() : new TreeMap<>(detail));
        byte[] hash = digest(previous, canonicalText(tenant.orgId(), chainKey, seq, at, tenant.practitionerId(), facilityId,
                action, entityType, id, reason, detailJson));
        jdbc.sql("""
                INSERT INTO audit_event (org_id, chain_key, seq, at, actor_id, facility_id, action, entity_type, entity_id,
                                         reason, detail, prev_hash, hash)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?, ?)""")
                .params(tenant.orgId(), chainKey, seq, Timestamp.from(at), tenant.practitionerId(), facilityId, action,
                        entityType, id, reason, detailJson, previous, hash)
                .update();
        jdbc.sql("UPDATE audit_chain SET last_seq = ?, last_hash = ? WHERE org_id = ? AND chain_key = ?")
                .params(seq, hash, tenant.orgId(), chainKey).update();
    }

    /** Re-walks one chain from the start and reports the first event that does not match its hash. */
    @Transactional(readOnly = true)
    public Verification verify(String chainKey) {
        TenantContext.Tenant tenant = TenantContext.require();
        byte[] previous = GENESIS;
        long expectedSeq = 1;
        long checked = 0;
        var rows = jdbc.sql("""
                SELECT seq, at, actor_id, facility_id, action, entity_type, entity_id, reason, detail::text AS detail,
                       prev_hash, hash
                  FROM audit_event WHERE org_id = ? AND chain_key = ? ORDER BY seq""")
                .params(tenant.orgId(), chainKey)
                .query((rs, n) -> new Object[] {rs.getLong("seq"), rs.getObject("at", OffsetDateTime.class).toInstant(),
                        rs.getObject("actor_id", UUID.class), rs.getObject("facility_id", UUID.class), rs.getString("action"),
                        rs.getString("entity_type"), rs.getString("entity_id"), rs.getString("reason"), rs.getString("detail"),
                        rs.getBytes("prev_hash"), rs.getBytes("hash")}).list();
        for (Object[] r : rows) {
            long seq = (Long) r[0];
            if (seq != expectedSeq || !java.util.Arrays.equals(previous, (byte[]) r[9])) {
                return new Verification(chainKey, false, checked, seq);
            }
            String detail = write(read((String) r[8]));
            byte[] expected = digest(previous, canonicalText(tenant.orgId(), chainKey, seq, (Instant) r[1], (UUID) r[2],
                    (UUID) r[3], (String) r[4], (String) r[5], (String) r[6], (String) r[7], detail));
            if (!java.util.Arrays.equals(expected, (byte[]) r[10])) {
                return new Verification(chainKey, false, checked, seq);
            }
            previous = (byte[]) r[10];
            expectedSeq++;
            checked++;
        }
        return new Verification(chainKey, true, checked, null);
    }

    @Transactional(readOnly = true)
    public List<String> chainKeys() {
        return jdbc.sql("SELECT chain_key FROM audit_chain WHERE org_id = ? ORDER BY chain_key")
                .param(TenantContext.require().orgId()).query(String.class).list();
    }

    @Transactional(readOnly = true)
    public List<Event> forEntity(String entityType, String entityId, int limit) {
        return jdbc.sql("""
                SELECT seq, chain_key, at, actor_id, facility_id, action, entity_type, entity_id, reason, detail::text AS detail
                  FROM audit_event WHERE org_id = ? AND entity_type = ? AND entity_id = ? ORDER BY at DESC, seq DESC LIMIT ?""")
                .params(TenantContext.require().orgId(), entityType, entityId, limit)
                .query((rs, n) -> new Event(rs.getLong("seq"), rs.getString("chain_key"), rs.getObject("at", OffsetDateTime.class).toInstant(),
                        rs.getObject("actor_id", UUID.class), rs.getObject("facility_id", UUID.class), rs.getString("action"),
                        rs.getString("entity_type"), rs.getString("entity_id"), rs.getString("reason"), rs.getString("detail")))
                .list();
    }

    private String canonicalText(UUID orgId, String chainKey, long seq, Instant at, UUID actor, UUID facility, String action,
                                 String entityType, String entityId, String reason, String detailJson) {
        long micros = at.getEpochSecond() * 1_000_000L + at.getNano() / 1_000;
        return String.join("|", orgId.toString(), chainKey, Long.toString(seq), Long.toString(micros), String.valueOf(actor),
                String.valueOf(facility), action, entityType, String.valueOf(entityId), String.valueOf(reason), detailJson);
    }

    private byte[] digest(byte[] previous, String text) {
        try {
            MessageDigest sha = MessageDigest.getInstance("SHA-256");
            sha.update(previous);
            return sha.digest(text.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private String write(Object value) {
        try {
            return canonical.writeValueAsString(value);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> read(String json) {
        try {
            return new TreeMap<>(canonical.readValue(json, Map.class));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    /** For the diagnostic hex view of a hash in tooling and tests. */
    public static String hex(byte[] bytes) {
        return HexFormat.of().formatHex(bytes);
    }
}
