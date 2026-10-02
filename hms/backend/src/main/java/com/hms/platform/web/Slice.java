package com.hms.platform.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Map;

/**
 * One page of a list plus the opaque cursor for the next. Lists are keyset-paged: the cost of a page
 * does not grow with how deep it is, and a record added while paging cannot shift the page.
 */
public record Slice<T>(List<T> data, String nextCursor) {

    private static final ObjectMapper JSON = new ObjectMapper();
    public static final int DEFAULT_LIMIT = 25;
    public static final int MAX_LIMIT = 100;

    public static int limit(Integer requested) {
        if (requested == null) {
            return DEFAULT_LIMIT;
        }
        return Math.max(1, Math.min(requested, MAX_LIMIT));
    }

    public static String encode(Map<String, String> position) {
        try {
            return Base64.getUrlEncoder().withoutPadding().encodeToString(JSON.writeValueAsBytes(position));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @SuppressWarnings("unchecked")
    public static Map<String, String> decode(String cursor) {
        if (cursor == null || cursor.isBlank()) {
            return null;
        }
        try {
            return JSON.readValue(new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8), Map.class);
        } catch (Exception e) {
            throw ApiException.badRequest("bad_cursor", "That page cursor is not valid.");
        }
    }
}
