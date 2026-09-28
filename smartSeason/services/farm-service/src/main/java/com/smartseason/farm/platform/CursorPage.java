package com.smartseason.farm.platform;

import java.util.List;
import org.springframework.data.domain.Slice;

public record CursorPage<T>(List<T> items, String nextCursor, boolean hasMore) {

    public static <E, T> CursorPage<T> of(Slice<E> slice,
                                          List<T> mapped,
                                          java.util.function.Function<E, Cursor> toCursor) {
        List<E> rows = slice.getContent();

        String next = (slice.hasNext() && !rows.isEmpty())
                ? toCursor.apply(rows.get(rows.size() - 1)).encode()
                : null;
        return new CursorPage<>(mapped, next, slice.hasNext());
    }
}
