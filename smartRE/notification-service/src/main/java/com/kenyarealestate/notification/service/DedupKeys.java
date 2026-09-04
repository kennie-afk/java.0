package com.kenyarealestate.notification.service;

import com.kenyarealestate.notification.entity.Channel;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.UUID;

public final class DedupKeys {

    private DedupKeys() {}

    public static String forEvent(String eventType, String eventId, UUID userId, Channel channel) {
        return sha256(eventType + ":" + eventId + ":" + userId + ":" + channel);
    }

    public static String sha256(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(md.digest(input.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
