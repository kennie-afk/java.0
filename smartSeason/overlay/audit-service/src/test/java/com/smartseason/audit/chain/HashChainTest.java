package com.smartseason.audit.chain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class HashChainTest {

    private static final Instant T0 = Instant.parse("2026-03-02T10:00:00Z");
    private final UUID actor = UUID.randomUUID();

    private AuditEntry entry(long sequence, String action) {
        return new AuditEntry(sequence, "payout-service", actor, "ADMIN", action,
                "PayoutBatch", "PB-1", "SUCCESS", T0.plusSeconds(sequence), "10.0.0.1",
                "{\"amount\":4500}");
    }

    private record Chain(List<AuditEntry> entries, List<String> hashes, List<String> previous) {
    }

    private Chain build(int size) {
        List<AuditEntry> entries = new ArrayList<>();
        List<String> hashes = new ArrayList<>();
        List<String> previous = new ArrayList<>();

        String prior = HashChain.GENESIS;
        for (int i = 0; i < size; i++) {
            AuditEntry entry = entry(i, "APPROVE_" + i);
            String hash = HashChain.hash(entry, prior);
            entries.add(entry);
            previous.add(prior);
            hashes.add(hash);
            prior = hash;
        }
        return new Chain(entries, hashes, previous);
    }

    @Test
    @DisplayName("an untouched chain verifies as intact")
    void intactChainVerifies() {
        Chain chain = build(5);

        assertThat(HashChain.verify(chain.entries(), chain.hashes(), chain.previous()).intact())
                .isTrue();
    }

    @Test
    @DisplayName("hashing is deterministic, so the same record always yields the same hash")
    void hashingIsDeterministic() {
        assertThat(HashChain.hash(entry(1, "APPROVE"), HashChain.GENESIS))
                .isEqualTo(HashChain.hash(entry(1, "APPROVE"), HashChain.GENESIS));
    }

    @Test
    @DisplayName("changing any field changes the hash")
    void anyChangeAltersTheHash() {
        String original = HashChain.hash(entry(1, "APPROVE"), HashChain.GENESIS);

        assertThat(HashChain.hash(entry(1, "REJECT"), HashChain.GENESIS)).isNotEqualTo(original);
        assertThat(HashChain.hash(entry(2, "APPROVE"), HashChain.GENESIS)).isNotEqualTo(original);
    }

    @Test
    @DisplayName("field boundaries cannot be forged by shifting content between adjacent fields")
    void lengthPrefixingPreventsFieldConfusion() {
        AuditEntry left = new AuditEntry(1, "payout", actor, "AD", "MIN_APPROVE",
                "R", "1", "SUCCESS", T0, "ip", "d");
        AuditEntry right = new AuditEntry(1, "payout", actor, "ADMIN", "_APPROVE",
                "R", "1", "SUCCESS", T0, "ip", "d");

        assertThat(HashChain.hash(left, HashChain.GENESIS))
                .isNotEqualTo(HashChain.hash(right, HashChain.GENESIS));
    }

    @Test
    @DisplayName("editing a record in the middle is detected at exactly that record")
    void tamperedRecordIsDetected() {
        Chain chain = build(5);

        List<AuditEntry> tampered = new ArrayList<>(chain.entries());
        AuditEntry original = tampered.get(2);
        tampered.set(2, new AuditEntry(original.sequence(), original.serviceName(),
                original.actorUserId(), original.actorRole(), "APPROVE_TAMPERED",
                original.resourceType(), original.resourceId(), original.outcome(),
                original.occurredAt(), original.ipAddress(), original.details()));

        HashChain.Verification result =
                HashChain.verify(tampered, chain.hashes(), chain.previous());

        assertThat(result.intact()).isFalse();
        assertThat(result.brokenAtSequence()).isEqualTo(2);
        assertThat(result.reason()).contains("does not match its stored hash");
    }

    @Test
    @DisplayName("deleting a record breaks the sequence and is detected")
    void deletedRecordIsDetected() {
        Chain chain = build(5);

        List<AuditEntry> entries = new ArrayList<>(chain.entries());
        List<String> hashes = new ArrayList<>(chain.hashes());
        List<String> previous = new ArrayList<>(chain.previous());
        entries.remove(2);
        hashes.remove(2);
        previous.remove(2);

        HashChain.Verification result = HashChain.verify(entries, hashes, previous);

        assertThat(result.intact()).isFalse();
        assertThat(result.reason()).contains("sequence gap");
    }

    @Test
    @DisplayName("re-hashing a tampered record still fails, because its successor's link breaks")
    void rehashingTamperedRecordStillFails() {
        Chain chain = build(4);

        List<AuditEntry> entries = new ArrayList<>(chain.entries());
        List<String> hashes = new ArrayList<>(chain.hashes());
        List<String> previous = new ArrayList<>(chain.previous());

        AuditEntry original = entries.get(1);
        AuditEntry edited = new AuditEntry(original.sequence(), original.serviceName(),
                original.actorUserId(), "SUPER_ADMIN", original.action(),
                original.resourceType(), original.resourceId(), original.outcome(),
                original.occurredAt(), original.ipAddress(), original.details());
        entries.set(1, edited);
        hashes.set(1, HashChain.hash(edited, previous.get(1)));

        HashChain.Verification result = HashChain.verify(entries, hashes, previous);

        assertThat(result.intact())
                .as("record 2's stored previous-hash still points at the original record 1")
                .isFalse();
        assertThat(result.brokenAtSequence()).isEqualTo(2);
    }

    @Test
    @DisplayName("an empty chain is trivially intact")
    void emptyChainIsIntact() {
        assertThat(HashChain.verify(List.of(), List.of(), List.of()).intact()).isTrue();
    }

    @Test
    @DisplayName("mismatched record and hash counts are rejected outright")
    void mismatchedCountsRejected() {
        Chain chain = build(3);

        assertThat(HashChain.verify(chain.entries(), chain.hashes().subList(0, 2),
                chain.previous()).intact()).isFalse();
    }

    @Test
    @DisplayName("the first record links to the genesis hash")
    void firstRecordLinksToGenesis() {
        assertThat(build(1).previous().getFirst()).isEqualTo(HashChain.GENESIS);
    }

    @Test
    @DisplayName("a null field is hashed distinctly from an empty one is not required, but null is safe")
    void nullFieldsAreSafe() {
        AuditEntry sparse = new AuditEntry(0, null, null, null, null, null, null, null,
                null, null, null);

        assertThat(HashChain.hash(sparse, null)).hasSize(64);
    }
}
