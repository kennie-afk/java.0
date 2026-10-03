package com.hms.platform.storage;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;
import java.util.regex.Pattern;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Files on the local disk, with signed, expiring links standing in for an S3 pre-signed URL. Fine for development and a single node;
 * a cluster of API pods needs shared storage, which this build does not provide.
 */
public final class LocalObjectStore implements ObjectStore {

    static final Pattern KEY = Pattern.compile("^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}/[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$");
    private static final Pattern TYPE = Pattern.compile("^image/(png|jpeg)$");

    private final Path root;
    private final byte[] secret;

    public LocalObjectStore(Path root, byte[] secret) {
        this.root = root.toAbsolutePath().normalize();
        this.secret = secret;
        try {
            Files.createDirectories(this.root);
        } catch (IOException e) {
            throw new IllegalStateException("The object store folder " + this.root + " cannot be created. Set HMS_STORAGE_PATH to a writable folder.", e);
        }
        if (!Files.isWritable(this.root)) {
            throw new IllegalStateException("The object store folder " + this.root + " is not writable. Set HMS_STORAGE_PATH to a writable folder.");
        }
    }

    private Path path(String key) {
        if (key == null || !KEY.matcher(key).matches()) {
            throw new IllegalArgumentException("not a valid object key");
        }
        Path p = root.resolve(key).normalize();
        if (!p.startsWith(root)) {
            throw new IllegalArgumentException("not a valid object key");
        }
        return p;
    }

    @Override
    public void put(String key, byte[] data) {
        Path target = path(key);
        try {
            Files.createDirectories(target.getParent());
            // Write beside the target and rename, so a reader never sees half a file and a crash leaves no truncated image.
            Path tmp = Files.createTempFile(target.getParent(), ".upload-", ".tmp");
            Files.write(tmp, data);
            Files.move(tmp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public Optional<byte[]> read(String key) {
        Path p = path(key);
        try {
            return Files.isRegularFile(p) ? Optional.of(Files.readAllBytes(p)) : Optional.empty();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public String presignGet(String key, String contentType, Duration ttl) {
        path(key);
        if (!TYPE.matcher(contentType).matches()) {
            throw new IllegalArgumentException("unsupported content type");
        }
        long exp = Instant.now().plus(ttl).getEpochSecond();
        String t = contentType.substring("image/".length());
        return "/v1/objects/" + key + "?exp=" + exp + "&t=" + t + "&sig=" + sign(key, exp, t);
    }

    /** Checks a link produced by {@link #presignGet}: the signature must match and the time limit must not have passed. */
    public boolean verify(String key, long exp, String type, String sig) {
        if (key == null || !KEY.matcher(key).matches() || type == null || !(type.equals("png") || type.equals("jpeg")) || sig == null) {
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

    @Override
    public boolean isLocal() {
        return true;
    }
}
