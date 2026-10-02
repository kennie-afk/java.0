package com.mara.sync.journal;

import java.util.List;

/**
 * What an upload achieved. {@code acceptedThrough} is the highest sequence the server now
 * holds a verified, chained copy of; the terminal advances its own sync cursor to exactly
 * that and never further. A refused entry, a gap or a break is reported, never swallowed.
 */
public record IngestResult(
        long acceptedThrough,
        int accepted,
        int duplicates,
        Gap gap,
        List<Refusal> refused,
        List<String> flagged) {

    public record Gap(long from, long to) {
    }

    public record Refusal(long sequence, String reason) {
    }
}
