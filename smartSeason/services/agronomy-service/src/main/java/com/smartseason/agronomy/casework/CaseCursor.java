package com.smartseason.agronomy.casework;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;

/** Position in a queue: the last row seen, by the same three keys the queue is sorted on. */
public record CaseCursor(int priority, Instant createdAt, UUID id) {

    public String encode() {
        String raw = priority + "|" + createdAt + "|" + id;
        return Base64.getUrlEncoder().withoutPadding().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    /** Null for no cursor or one that cannot be read, which starts from the top. */
    public static CaseCursor decode(String cursor) {
        if (cursor == null || cursor.isBlank()) {
            return null;
        }
        try {
            String[] parts = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8).split("\\|");
            return new CaseCursor(Integer.parseInt(parts[0]), Instant.parse(parts[1]), UUID.fromString(parts[2]));
        } catch (RuntimeException ex) {
            return null;
        }
    }

    public static CaseCursor of(AdvisoryCase c) {
        return new CaseCursor(c.getPriority(), c.getCreatedAt(), c.getId());
    }
}
