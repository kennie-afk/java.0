package com.smartseason.audit.chain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class HashChainLinkTest {

    private static AuditEntry entry(long sequence, String action) {
        return new AuditEntry(sequence, "web", UUID.fromString("00000000-0000-0000-0000-000000000001"),
                "WORKER", action, "TaskAssignment", "task-1", "SUCCESS",
                Instant.parse("2026-09-08T06:00:00Z"), "127.0.0.1", null);
    }

    /** Builds a chain the way AuditAppendService does, then verifies it. */
    private static void build(List<AuditEntry> entries, List<String> hashes, List<String> previous) {
        String prior = HashChain.GENESIS;
        for (AuditEntry entry : entries) {
            previous.add(prior);
            String hash = HashChain.hash(entry, prior);
            hashes.add(hash);
            prior = hash;
        }
    }

    @Test
    void anIntactChainVerifies() {
        List<AuditEntry> entries = List.of(entry(1, "TASK_STARTED"), entry(2, "TASK_STOPPED"));
        List<String> hashes = new ArrayList<>();
        List<String> previous = new ArrayList<>();
        build(entries, hashes, previous);

        assertThat(HashChain.verify(entries, hashes, previous).intact()).isTrue();
    }

    @Test
    void editingARecordBreaksTheChainAtThatRecord() {
        List<AuditEntry> entries = new ArrayList<>(List.of(entry(1, "TASK_STARTED"), entry(2, "TASK_STOPPED")));
        List<String> hashes = new ArrayList<>();
        List<String> previous = new ArrayList<>();
        build(entries, hashes, previous);

        // Someone rewrites the second entry to hide that the task was stopped early.
        entries.set(1, entry(2, "TASK_ABANDONED"));

        HashChain.Verification result = HashChain.verify(entries, hashes, previous);
        assertThat(result.intact()).isFalse();
        assertThat(result.brokenAtSequence()).isEqualTo(2);
    }

    @Test
    void removingARecordBreaksTheSequence() {
        List<AuditEntry> entries = new ArrayList<>(
                List.of(entry(1, "TASK_STARTED"), entry(2, "TASK_STOPPED"), entry(3, "TASK_VERIFIED")));
        List<String> hashes = new ArrayList<>();
        List<String> previous = new ArrayList<>();
        build(entries, hashes, previous);

        entries.remove(1);
        hashes.remove(1);
        previous.remove(1);

        assertThat(HashChain.verify(entries, hashes, previous).intact()).isFalse();
    }
}
