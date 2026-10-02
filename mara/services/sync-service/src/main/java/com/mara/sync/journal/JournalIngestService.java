package com.mara.sync.journal;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mara.kit.auth.TerminalRecord;
import com.mara.platform.journal.ChainDigest;
import com.mara.platform.journal.ChainVerdict;
import com.mara.platform.journal.JournalEntry;
import com.mara.platform.journal.JournalVerifier;
import com.mara.platform.sale.SaleCheck;
import com.mara.platform.sale.SaleEntryVerifier;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Verifies an upload and extends the server's copy of a terminal's chain.
 *
 * <p>Three layers, each answering a different question. Per entry: is this exactly what the
 * terminal signed (body digest and chain digest recomputed from the sale, signature checked
 * against the enrolled key)? Per chain: does it continue what the server holds, without a
 * hole, a fork or a clock running backwards (the platform's {@link JournalVerifier})? Per
 * sale: is the arithmetic the terminal claims actually consistent ({@link SaleCheck})?
 *
 * <p>Nothing is trusted that can be recomputed. A failure at any layer becomes an exception
 * row an owner can see; only entries that pass the first two layers are stored, and a
 * sale that fails only the third is stored <em>and</em> flagged, because the till is
 * authoritative about what happened at its counter.
 *
 * <p>The head row is locked for the whole upload, so a retry racing its own original, or two
 * browser tabs of one till, are serialised rather than interleaved.
 */
@Service
public class JournalIngestService {

    public static final int MAX_BATCH = 500;

    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    private final JournalVerifier verifier = new JournalVerifier();

    public JournalIngestService(JdbcTemplate jdbc, ObjectMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    @Transactional
    public IngestResult ingest(TerminalRecord terminal, List<UploadedEntry> upload) {
        String terminalId = terminal.id();
        String tenantId = terminal.tenantId();
        List<IngestResult.Refusal> refused = new ArrayList<>();
        List<String> flagged = new ArrayList<>();

        jdbc.update("""
                INSERT INTO chain_head (terminal_id, tenant_id, head_digest, genesis_digest)
                VALUES (?, ?, ?, ?) ON CONFLICT (terminal_id) DO NOTHING""",
                terminalId, tenantId, ChainDigest.GENESIS, ChainDigest.GENESIS);
        Map<String, Object> head = jdbc.queryForMap(
                "SELECT last_sequence, head_digest, genesis_digest, last_epoch_second, last_nano "
                        + "FROM chain_head WHERE terminal_id = ? FOR UPDATE", terminalId);
        long lastSequence = ((Number) head.get("last_sequence")).longValue();
        JournalVerifier.Cursor cursor = new JournalVerifier.Cursor(
                terminalId, lastSequence, (byte[]) head.get("head_digest"), (byte[]) head.get("genesis_digest"),
                Instant.ofEpochSecond(((Number) head.get("last_epoch_second")).longValue(),
                        ((Number) head.get("last_nano")).intValue()));

        int duplicates = 0;
        List<JournalEntry> candidates = new ArrayList<>();
        Map<Long, UploadedEntry> bySequence = new HashMap<>();

        List<UploadedEntry> ordered = upload.stream().sorted(Comparator.comparingLong(UploadedEntry::sequence)).toList();
        for (UploadedEntry e : ordered) {
            if (!terminalId.equals(e.terminalId())) {
                raise(tenantId, terminalId, e.sequence(), "FOREIGN_ENTRY",
                        "entry names terminal " + e.terminalId() + " but was uploaded by " + terminalId);
                refused.add(new IngestResult.Refusal(e.sequence(), "FOREIGN_ENTRY"));
                continue;
            }
            if (e.sale() == null || e.bodyDigest() == null || e.previousDigest() == null
                    || e.digest() == null || e.signature() == null || e.sequence() < 1) {
                raise(tenantId, terminalId, Math.max(e.sequence(), 0), "MALFORMED", "entry is missing required fields");
                refused.add(new IngestResult.Refusal(e.sequence(), "MALFORMED"));
                continue;
            }
            SaleEntryVerifier.Verified v = SaleEntryVerifier.verify(
                    terminal.publicKey(), terminalId, e.sequence(), e.epochSecond(), e.nano(), e.sale(),
                    e.bodyDigest(), e.previousDigest(), e.digest(), e.signature());
            if (v.verdict() != SaleEntryVerifier.Verdict.OK) {
                String kind = switch (v.verdict()) {
                    case BODY_DIGEST_MISMATCH -> "BAD_BODY_DIGEST";
                    case DIGEST_MISMATCH -> "BAD_DIGEST";
                    case BAD_SIGNATURE -> "BAD_SIGNATURE";
                    default -> "MALFORMED";
                };
                raise(tenantId, terminalId, e.sequence(), kind, "entry " + e.sequence() + " failed verification: " + v.verdict());
                refused.add(new IngestResult.Refusal(e.sequence(), kind));
                continue;
            }
            if (e.sequence() <= lastSequence) {
                byte[] stored = storedDigest(terminalId, e.sequence());
                if (stored != null && !ChainDigest.matches(stored, v.digest())) {
                    raise(tenantId, terminalId, e.sequence(), "FORKED_SEQUENCE",
                            "sequence " + e.sequence() + " was already accepted with digest "
                                    + ChainDigest.hex(stored) + "; the terminal now presents a different entry");
                    refused.add(new IngestResult.Refusal(e.sequence(), "FORKED_SEQUENCE"));
                } else {
                    duplicates++;
                }
                continue;
            }
            candidates.add(v.entry());
            bySequence.put(e.sequence(), e);
        }

        ChainVerdict verdict = verifier.verify(cursor, candidates);
        for (JournalEntry accepted : verdict.accepted()) {
            UploadedEntry e = bySequence.get(accepted.sequence());
            insertEntry(tenantId, e, accepted);
            SaleCheck.Result check = SaleCheck.check(e.sale());
            if (!check.consistent()) {
                String note = "sale at sequence " + e.sequence() + " is internally inconsistent: " + check.findings();
                raise(tenantId, terminalId, e.sequence(), "SALE_INCONSISTENT", note);
                flagged.add(note);
            }
        }
        JournalVerifier.Cursor advanced = verifier.advance(cursor, verdict);
        if (advanced.lastSequence() != lastSequence) {
            Instant at = advanced.lastOccurredAt();
            jdbc.update("""
                    UPDATE chain_head SET last_sequence = ?, head_digest = ?, genesis_digest = ?,
                           last_epoch_second = ?, last_nano = ?, updated_at = now()
                     WHERE terminal_id = ?""",
                    advanced.lastSequence(), advanced.headDigest(), advanced.genesisDigest(),
                    at.getEpochSecond(), at.getNano(), terminalId);
        }

        // A gap closes itself when the missing entries finally arrive and the chain passes it.
        jdbc.update("""
                UPDATE sync_exception SET resolved_at = now(), resolution = 'missing entries arrived'
                 WHERE terminal_id = ? AND kind = 'GAP' AND resolved_at IS NULL AND sequence <= ?""",
                terminalId, advanced.lastSequence());

        IngestResult.Gap gap = null;
        for (ChainVerdict.SequenceGap g : verdict.gaps()) {
            gap = new IngestResult.Gap(g.fromSequence(), g.toSequence());
            raise(tenantId, terminalId, g.fromSequence(), "GAP",
                    "sequences " + g.fromSequence() + ".." + g.toSequence() + " are missing from this terminal's upload; "
                            + "reconciliation is held open at " + advanced.lastSequence());
        }
        for (ChainVerdict.ChainBreak b : verdict.breaks()) {
            raise(tenantId, terminalId, b.sequence(), b.reason().name(), b.detail());
            refused.add(new IngestResult.Refusal(b.sequence(), b.reason().name()));
        }
        return new IngestResult(advanced.lastSequence(), verdict.accepted().size(), duplicates, gap, refused, flagged);
    }

    private byte[] storedDigest(String terminalId, long sequence) {
        List<byte[]> rows = jdbc.query(
                "SELECT digest FROM journal_entry WHERE terminal_id = ? AND sequence = ?",
                (rs, i) -> rs.getBytes(1), terminalId, sequence);
        return rows.isEmpty() ? null : rows.get(0);
    }

    private void insertEntry(String tenantId, UploadedEntry e, JournalEntry accepted) {
        String saleJson;
        try {
            saleJson = json.writeValueAsString(e.sale());
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("a verified sale could not be serialised", ex);
        }
        HexFormat hex = HexFormat.of();
        jdbc.update("""
                INSERT INTO journal_entry (terminal_id, sequence, tenant_id, epoch_second, nano, sale,
                                           body_digest, previous_digest, digest, signature)
                VALUES (?, ?, ?, ?, ?, ?::jsonb, ?, ?, ?, ?)""",
                e.terminalId(), e.sequence(), tenantId, e.epochSecond(), e.nano(), saleJson,
                accepted.bodyDigest(), accepted.previousDigest(), ChainDigest.of(accepted), hex.parseHex(e.signature()));
    }

    private void raise(String tenantId, String terminalId, long sequence, String kind, String detail) {
        jdbc.update("""
                INSERT INTO sync_exception (tenant_id, terminal_id, sequence, kind, detail)
                VALUES (?, ?, ?, ?, ?) ON CONFLICT DO NOTHING""",
                tenantId, terminalId, Math.max(sequence, 0), kind, detail);
    }
}
