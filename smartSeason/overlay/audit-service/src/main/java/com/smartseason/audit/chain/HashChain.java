package com.smartseason.audit.chain;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;

public final class HashChain {

    public static final String GENESIS =
            "0000000000000000000000000000000000000000000000000000000000000000";

    private HashChain() {
    }

    public static String hash(AuditEntry entry, String previousHash) {
        StringBuilder canonical = new StringBuilder();
        append(canonical, Long.toString(entry.sequence()));
        append(canonical, previousHash == null ? GENESIS : previousHash);
        append(canonical, entry.serviceName());
        append(canonical, entry.actorUserId() == null ? null : entry.actorUserId().toString());
        append(canonical, entry.actorRole());
        append(canonical, entry.action());
        append(canonical, entry.resourceType());
        append(canonical, entry.resourceId());
        append(canonical, entry.outcome());
        append(canonical, entry.occurredAt() == null ? null : entry.occurredAt().toString());
        append(canonical, entry.ipAddress());
        append(canonical, entry.details());

        return sha256(canonical.toString());
    }

    public record Verification(boolean intact, long brokenAtSequence, String reason) {

        public static Verification ok() {
            return new Verification(true, -1, null);
        }

        public static Verification broken(long sequence, String reason) {
            return new Verification(false, sequence, reason);
        }
    }

    public static Verification verify(List<AuditEntry> entries, List<String> storedHashes,
                                      List<String> storedPreviousHashes) {
        if (entries.size() != storedHashes.size()
                || entries.size() != storedPreviousHashes.size()) {
            return Verification.broken(-1, "record and hash counts differ");
        }

        String expectedPrevious = GENESIS;
        long expectedSequence = entries.isEmpty() ? 0 : entries.getFirst().sequence();

        for (int i = 0; i < entries.size(); i++) {
            AuditEntry entry = entries.get(i);

            if (entry.sequence() != expectedSequence) {
                return Verification.broken(entry.sequence(),
                        "sequence gap: expected " + expectedSequence);
            }
            if (!expectedPrevious.equals(storedPreviousHashes.get(i))) {
                return Verification.broken(entry.sequence(),
                        "previous-hash link does not match the preceding record");
            }

            String recomputed = hash(entry, storedPreviousHashes.get(i));
            if (!recomputed.equals(storedHashes.get(i))) {
                return Verification.broken(entry.sequence(),
                        "record content does not match its stored hash");
            }

            expectedPrevious = storedHashes.get(i);
            expectedSequence++;
        }

        return Verification.ok();
    }

    private static void append(StringBuilder target, String value) {
        String safe = value == null ? "" : value;
        target.append(safe.length()).append(':').append(safe);
    }

    private static String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is required but unavailable", ex);
        }
    }
}
