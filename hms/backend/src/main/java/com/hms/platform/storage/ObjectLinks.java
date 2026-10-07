package com.hms.platform.storage;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.regex.Pattern;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Signed, expiring links to {@code /v1/objects/...}, issued and checked by this API whatever store holds the bytes. With an S3 store
 * in the default "proxy" mode the browser never talks to the bucket, so the bucket needs no public address.
 */
public final class ObjectLinks {

    private static final Pattern TYPE = Pattern.compile("^image/(png|jpeg)$");

    private final byte[] secret;

    public ObjectLinks(byte[] secret) {
        this.secret = secret;
    }

    public String presign(String key, String contentType, Duration ttl) {
        if (key == null || !LocalObjectStore.KEY.matcher(key).matches()) {
            throw new IllegalArgumentException("not a valid object key");
        }
        if (!TYPE.matcher(contentType).matches()) {
            throw new IllegalArgumentException("unsupported content type");
        }
        long exp = Instant.now().plus(ttl).getEpochSecond();
        String t = contentType.substring("image/".length());
        return "/v1/objects/" + key + "?exp=" + exp + "&t=" + t + "&sig=" + sign(key, exp, t);
    }

    /** Checks a link produced by {@link #presign}: the signature must match and the time limit must not have passed. */
    public boolean verify(String key, long exp, String type, String sig) {
        if (key == null || !LocalObjectStore.KEY.matcher(key).matches() || type == null || !(type.equals("png") || type.equals("jpeg")) || sig == null) {
            return false;
        }
        if (Instant.now().getEpochSecond() > exp) {
            return false;
        }
        return MessageDigest.isEqual(sign(key, exp, type).getBytes(StandardCharsets.UTF_8), sig.getBytes(StandardCharsets.UTF_8));
    }

    private String sign(String key, long exp, String type) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret, "HmacSHA256"));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(mac.doFinal((key + "|" + exp + "|" + type).getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }
}
