package com.smartseason.farm.platform;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.UUID;

public record Cursor(Instant createdAt, UUID id) {

    public Cursor {

        createdAt = createdAt == null ? null : createdAt.truncatedTo(ChronoUnit.MICROS);
    }

    public String encode() {
        String raw = createdAt.toEpochMilli() + ":" + createdAt.getNano() + ":" + id;
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    public static Cursor decode(String encoded) {
        if (encoded == null || encoded.isBlank()) {
            return null;
        }
        try {
            String raw = new String(Base64.getUrlDecoder().decode(encoded), StandardCharsets.UTF_8);
            String[] parts = raw.split(":", 3);
            if (parts.length != 3) {
                return null;
            }
            Instant at = Instant.ofEpochMilli(Long.parseLong(parts[0]))
                    .plusNanos(Long.parseLong(parts[1]) % 1_000_000L);
            return new Cursor(at, UUID.fromString(parts[2]));
        } catch (RuntimeException ex) {
            return null;
        }
    }
}
