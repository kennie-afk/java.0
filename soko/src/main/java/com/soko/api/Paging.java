package com.soko.api;

import com.soko.persistence.OfferRepository;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;

/**
 * Shared paging and search rules for list endpoints.
 *
 * <p>The response body stays a plain JSON array (existing clients and tools read it as one); the
 * total and whether more rows follow travel in {@code X-Total-Count} and {@code X-Has-More}, so a
 * caller can tell a full list from a truncated one instead of silently seeing the first page.
 */
public final class Paging {

    public static final int DEFAULT_LIMIT = 50;
    public static final int MAX_LIMIT = 200;

    private Paging() {}

    public static int limit(int requested) {
        return Math.min(Math.max(requested, 1), MAX_LIMIT);
    }

    public static int page(int requested) {
        return Math.max(requested, 0);
    }

    public static Pageable pageable(int page, int limit) {
        return PageRequest.of(page(page), limit(limit));
    }

    /** A lower-cased LIKE pattern matching anywhere; user-typed % _ and \ are matched literally. */
    public static String like(String q) {
        if (q == null || q.isBlank()) {
            return "%";
        }
        String escaped = q.trim().toLowerCase()
                .replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
        return "%" + escaped + "%";
    }

    public static <T> ResponseEntity<List<T>> respond(List<T> items, long total, int page, int limit) {
        boolean hasMore = (long) page(page) * limit(limit) + items.size() < total;
        return ResponseEntity.ok()
                .header("X-Total-Count", Long.toString(total))
                .header("X-Has-More", Boolean.toString(hasMore))
                .body(items);
    }

    public static ResponseEntity<List<Map<String, Object>>> storefront(
            OfferRepository offers, UUID tenantId, String q, int page, int limit) {
        int size = limit(limit);
        String pattern = like(q);
        List<Map<String, Object>> rows = offers
                .storefront(tenantId, pattern, size, (long) page(page) * size).stream()
                .map(Paging::storefrontRow)
                .toList();
        return respond(rows, offers.countStorefront(tenantId, pattern), page, size);
    }

    private static Map<String, Object> storefrontRow(Object[] r) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", r[0]);
        row.put("sku", r[1]);
        row.put("name", r[2]);
        row.put("category", r[3]);
        row.put("unit", r[4]);
        row.put("perishable", r[5]);
        row.put("chilled", r[6]);
        row.put("shelfLifeHours", r[7]);
        row.put("priceCents", r[8]);
        row.put("inStock", ((Number) r[9]).longValue());
        row.put("photoUrl", r[10]);
        return row;
    }
}
