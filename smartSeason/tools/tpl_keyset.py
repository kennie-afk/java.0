"""Keyset pagination: the fix for deep pages.

`OFFSET 50000 LIMIT 25` does not skip 50,000 rows, it *reads* them and throws them away.
Page 1 is instant and page 2,000 is a table scan, so the cost of a list endpoint grows
with how far a caller has walked rather than with how much they asked for. Nobody notices
on a demo database and everybody notices on a real one.

Keyset paging instead remembers where the last page ended and asks for rows after it:

    WHERE (created_at, id) < (:at, :id) ORDER BY created_at DESC, id DESC LIMIT 25

With an index on `(tenant_id, created_at DESC, id DESC)` that is an index seek to the
right position followed by a sequential read of 25 entries. Page 2,000 costs exactly what
page 1 costs.

`id` is in the key because `created_at` is not unique: two rows written in the same
microsecond would otherwise straddle a page boundary and one of them would be skipped or
repeated. The pair is unique because `id` is.

Timestamps are truncated to microseconds before they go into a cursor. Postgres stores
`timestamptz` to microsecond precision while `Instant` carries nanoseconds, so an
untruncated value round-trips to something fractionally larger than what is stored and
the comparison silently drops a row. That exact mismatch already broke the audit chain
once here; this is the same bug with a different symptom.

What keyset paging gives up: you cannot jump to page 50, only forward from where you are.
That is the right trade for feeds, exports and infinite scroll, and the wrong one for a
numbered pager. Both are offered - the existing offset endpoint is untouched.
"""

CURSOR = '''package com.smartseason.{pkg}.platform;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.UUID;

/**
 * An opaque position in a keyset-paged list.
 *
 * <p>Encoded rather than exposed as two query parameters so that callers treat it as
 * opaque. The moment a client builds its own cursor, the sort key can never change
 * without breaking it.
 */
public record Cursor(Instant createdAt, UUID id) {{

    public Cursor {{
        // Postgres keeps timestamptz to microseconds. An Instant carries nanoseconds, so
        // an untruncated value compares as slightly later than the row it came from and
        // that row's neighbours get skipped.
        createdAt = createdAt == null ? null : createdAt.truncatedTo(ChronoUnit.MICROS);
    }}

    public String encode() {{
        String raw = createdAt.toEpochMilli() + ":" + createdAt.getNano() + ":" + id;
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }}

    /**
     * Decodes a cursor, or returns null if it is absent or unreadable.
     *
     * <p>A malformed cursor is treated as no cursor - the caller gets the first page
     * rather than an error. Cursors travel in URLs and get truncated, re-encoded and
     * pasted; failing the request would turn a cosmetic accident into an outage.
     */
    public static Cursor decode(String encoded) {{
        if (encoded == null || encoded.isBlank()) {{
            return null;
        }}
        try {{
            String raw = new String(Base64.getUrlDecoder().decode(encoded), StandardCharsets.UTF_8);
            String[] parts = raw.split(":", 3);
            if (parts.length != 3) {{
                return null;
            }}
            Instant at = Instant.ofEpochMilli(Long.parseLong(parts[0]))
                    .plusNanos(Long.parseLong(parts[1]) % 1_000_000L);
            return new Cursor(at, UUID.fromString(parts[2]));
        }} catch (RuntimeException ex) {{
            return null;
        }}
    }}
}}
'''


CURSOR_PAGE = '''package com.smartseason.{pkg}.platform;

import java.util.List;
import org.springframework.data.domain.Slice;

/**
 * A keyset-paged result: the rows, and where to continue from.
 *
 * <p>There is deliberately no total. Counting is the expensive half of a paged read and
 * a caller walking a feed does not need one; the offset endpoint still offers a cached
 * total for the screens that render "1-25 of N".
 */
public record CursorPage<T>(List<T> items, String nextCursor, boolean hasMore) {{

    /**
     * @param slice    the rows, fetched with the keyset predicate
     * @param toCursor how to derive a cursor from the last row
     */
    public static <E, T> CursorPage<T> of(Slice<E> slice,
                                          List<T> mapped,
                                          java.util.function.Function<E, Cursor> toCursor) {{
        List<E> rows = slice.getContent();
        // The cursor comes from the last row actually returned, never from the request.
        // Deriving it from the request is how a page boundary drifts and rows go missing.
        String next = (slice.hasNext() && !rows.isEmpty())
                ? toCursor.apply(rows.get(rows.size() - 1)).encode()
                : null;
        return new CursorPage<>(mapped, next, slice.hasNext());
    }}
}}
'''


def migration(service, entities):
    """A composite index per table, matching the keyset ORDER BY exactly.

    Without these the keyset query is correct and no faster: Postgres would sort the
    tenant's whole history to find 25 rows. The column order and the direction both
    matter - an index on (tenant_id, created_at) alone still leaves the id tie-break to a
    sort, and a mismatched direction cannot be walked backwards for a DESC scan.
    """
    lines = [
        "-- Keyset pagination indexes.",
        "--",
        "-- These match the ORDER BY of the cursor queries exactly: (tenant_id, created_at DESC,",
        "-- id DESC). An index that differs in column order or direction is not a partial win,",
        "-- it is unused - Postgres falls back to sorting the tenant's entire history to return",
        "-- twenty-five rows, which is the cost keyset paging exists to remove.",
        "--",
        "-- Forward-only. V1 is applied everywhere and must never be edited; changing an applied",
        "-- migration changes its checksum and blocks start-up.",
        "",
    ]
    for name, table, _fields in entities:
        lines.append(
            f"CREATE INDEX IF NOT EXISTS ix_{table}_keyset\n"
            f"    ON {table} (tenant_id, created_at DESC, id DESC);"
        )
    return "\n".join(lines) + "\n"
