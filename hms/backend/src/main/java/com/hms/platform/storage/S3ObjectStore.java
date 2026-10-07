package com.hms.platform.storage;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.regex.Pattern;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * An S3-compatible store (AWS S3, MinIO, Ceph...) spoken to with plain HTTP and AWS Signature Version 4, so no SDK is needed. Path-style
 * addressing ({@code <endpoint>/<bucket>/<key>}), which MinIO and every S3-compatible server accept.
 *
 * Verified: the signing routine reproduces AWS's published pre-signed URL example (see the test), and the store works against a
 * stub server and against MinIO. NOT verified against AWS S3 itself.
 *
 * Keys are {@code <orgId>/<objectId>}, so every object sits under its tenant's prefix. Each upload carries its SHA-256 as
 * {@code x-amz-content-sha256}, which the server checks against the bytes it received: a corrupted upload is refused, not stored.
 */
public final class S3ObjectStore implements ObjectStore {

    /**
     * @param publicEndpoint when set, links are native S3 pre-signed URLs against this address ("direct": the browser fetches from
     *     the bucket, which must then be reachable from browsers). When blank, links point at this API, which fetches the object
     *     from the bucket itself ("proxy", the default: the bucket stays private to the cluster).
     */
    public record Config(String endpoint, String publicEndpoint, String region, String bucket, String accessKey, String secretKey) {}

    private static final Pattern TYPE = Pattern.compile("^image/(png|jpeg)$");
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'").withZone(ZoneOffset.UTC);
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("yyyyMMdd").withZone(ZoneOffset.UTC);
    private static final String EMPTY_SHA256 = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855";

    private final Config config;
    private final URI endpoint;
    private final URI publicEndpoint;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

    private final ObjectLinks links;

    public S3ObjectStore(Config config, ObjectLinks links) {
        this.config = config;
        this.links = links;
        this.endpoint = URI.create(trimSlash(config.endpoint()));
        this.publicEndpoint = config.publicEndpoint() == null || config.publicEndpoint().isBlank() ? null : URI.create(trimSlash(config.publicEndpoint()));
    }

    @Override
    public void put(String key, byte[] data) {
        checkKey(key);
        String payloadHash = hex(sha256(data));
        HttpResponse<byte[]> r = send("PUT", key, data, payloadHash);
        if (r.statusCode() / 100 != 2) {
            throw new IllegalStateException("The object store refused the upload (HTTP " + r.statusCode() + ").");
        }
    }

    @Override
    public Optional<byte[]> read(String key) {
        checkKey(key);
        HttpResponse<byte[]> r = send("GET", key, null, EMPTY_SHA256);
        if (r.statusCode() == 404) {
            return Optional.empty();
        }
        if (r.statusCode() / 100 != 2) {
            throw new IllegalStateException("The object store refused the read (HTTP " + r.statusCode() + ").");
        }
        return Optional.of(r.body());
    }

    @Override
    public String presignGet(String key, String contentType, Duration ttl) {
        checkKey(key);
        if (!TYPE.matcher(contentType).matches()) {
            throw new IllegalArgumentException("unsupported content type");
        }
        if (publicEndpoint == null) {
            return links.presign(key, contentType, ttl);
        }
        long seconds = Math.min(Math.max(ttl.toSeconds(), 1), 604_800);
        // The object is served by the store itself, so the response headers that keep patient data from being cached or rendered
        // as anything but an image are part of what is signed.
        Map<String, String> extra = new TreeMap<>();
        extra.put("response-cache-control", "no-store");
        extra.put("response-content-disposition", "inline");
        extra.put("response-content-type", contentType);
        String host = authority(publicEndpoint);
        String path = pathFor(publicEndpoint, key);
        String query = presignQuery("GET", host, path, extra, config.accessKey(), config.secretKey(), config.region(), Instant.now(), seconds);
        return publicEndpoint.getScheme() + "://" + host + path + "?" + query;
    }

    @Override
    public boolean isLocal() {
        return false;
    }

    // ---- requests ----------------------------------------------------------------------------

    /** Creates the bucket if it is not there (S3 answers 409 when it already is yours). For stores whose bucket nobody else creates. */
    public void ensureBucket() {
        String prefix = endpoint.getPath() == null ? "" : endpoint.getPath();
        HttpResponse<byte[]> r = sendTo("PUT", prefix + "/" + uriEncode(config.bucket(), false), new byte[0], hex(sha256(new byte[0])));
        if (r.statusCode() / 100 != 2 && r.statusCode() != 409) {
            throw new IllegalStateException("The object store bucket '" + config.bucket() + "' cannot be created (HTTP " + r.statusCode() + "). Create it, or check the credentials.");
        }
    }

    private HttpResponse<byte[]> send(String method, String key, byte[] body, String payloadHash) {
        return sendTo(method, pathFor(endpoint, key), body, payloadHash);
    }

    private HttpResponse<byte[]> sendTo(String method, String path, byte[] body, String payloadHash) {
        String host = authority(endpoint);
        String stamp = STAMP.format(Instant.now());
        Map<String, String> headers = new TreeMap<>();
        headers.put("host", host);
        headers.put("x-amz-content-sha256", payloadHash);
        headers.put("x-amz-date", stamp);
        String auth = authorization(method, path, "", headers, payloadHash, config.accessKey(), config.secretKey(), config.region(), stamp);
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(endpoint.getScheme() + "://" + host + path)).timeout(Duration.ofSeconds(30))
                .header("x-amz-content-sha256", payloadHash).header("x-amz-date", stamp).header("Authorization", auth);
        b.method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofByteArray(body));
        try {
            return http.send(b.build(), HttpResponse.BodyHandlers.ofByteArray());
        } catch (IOException e) {
            throw new java.io.UncheckedIOException(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("interrupted while talking to the object store", e);
        }
    }

    private String pathFor(URI base, String key) {
        String prefix = base.getPath() == null ? "" : base.getPath();
        return prefix + "/" + uriEncode(config.bucket(), false) + "/" + uriEncode(key, false);
    }

    private static String authority(URI u) {
        return u.getPort() == -1 ? u.getHost() : u.getHost() + ":" + u.getPort();
    }

    private static void checkKey(String key) {
        if (key == null || !LocalObjectStore.KEY.matcher(key).matches()) {
            throw new IllegalArgumentException("not a valid object key");
        }
    }

    private static String trimSlash(String s) {
        return s.endsWith("/") ? s.substring(0, s.length() - 1) : s;
    }

    // ---- Signature Version 4 -----------------------------------------------------------------

    /** Header-signed request: returns the Authorization header value. {@code headers} must be lower-case names, sorted. */
    static String authorization(String method, String path, String query, Map<String, String> headers, String payloadHash,
                                String accessKey, String secretKey, String region, String stamp) {
        StringBuilder canonicalHeaders = new StringBuilder();
        StringBuilder signed = new StringBuilder();
        for (var h : new TreeMap<>(headers).entrySet()) {
            canonicalHeaders.append(h.getKey()).append(':').append(h.getValue().trim()).append('\n');
            signed.append(signed.length() == 0 ? "" : ";").append(h.getKey());
        }
        String canonical = method + "\n" + path + "\n" + query + "\n" + canonicalHeaders + "\n" + signed + "\n" + payloadHash;
        String day = stamp.substring(0, 8);
        String scope = day + "/" + region + "/s3/aws4_request";
        String toSign = "AWS4-HMAC-SHA256\n" + stamp + "\n" + scope + "\n" + hex(sha256(canonical.getBytes(StandardCharsets.UTF_8)));
        String signature = hex(hmac(signingKey(secretKey, day, region), toSign));
        return "AWS4-HMAC-SHA256 Credential=" + accessKey + "/" + scope + ", SignedHeaders=" + signed + ", Signature=" + signature;
    }

    /** Query-string signing (a pre-signed URL). Returns the complete query string, signature last. */
    static String presignQuery(String method, String host, String path, Map<String, String> extra, String accessKey, String secretKey,
                               String region, Instant now, long expiresSeconds) {
        String stamp = STAMP.format(now);
        String day = DAY.format(now);
        String scope = day + "/" + region + "/s3/aws4_request";
        Map<String, String> q = new TreeMap<>(extra);
        q.put("X-Amz-Algorithm", "AWS4-HMAC-SHA256");
        q.put("X-Amz-Credential", accessKey + "/" + scope);
        q.put("X-Amz-Date", stamp);
        q.put("X-Amz-Expires", Long.toString(expiresSeconds));
        q.put("X-Amz-SignedHeaders", "host");
        String canonicalQuery = canonicalQuery(q);
        String canonical = method + "\n" + path + "\n" + canonicalQuery + "\nhost:" + host + "\n\nhost\nUNSIGNED-PAYLOAD";
        String toSign = "AWS4-HMAC-SHA256\n" + stamp + "\n" + scope + "\n" + hex(sha256(canonical.getBytes(StandardCharsets.UTF_8)));
        return canonicalQuery + "&X-Amz-Signature=" + hex(hmac(signingKey(secretKey, day, region), toSign));
    }

    private static String canonicalQuery(Map<String, String> sorted) {
        StringBuilder sb = new StringBuilder();
        for (var e : new TreeMap<>(sorted).entrySet()) {
            sb.append(sb.length() == 0 ? "" : "&").append(uriEncode(e.getKey(), true)).append('=').append(uriEncode(e.getValue(), true));
        }
        return sb.toString();
    }

    /** RFC 3986 encoding as S3 wants it: only unreserved characters are left alone; '/' too when it is a path. */
    static String uriEncode(String s, boolean encodeSlash) {
        StringBuilder sb = new StringBuilder();
        for (byte b : s.getBytes(StandardCharsets.UTF_8)) {
            char c = (char) (b & 0xff);
            if ((c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == '-' || c == '_' || c == '.' || c == '~' || (c == '/' && !encodeSlash)) {
                sb.append(c);
            } else {
                sb.append('%').append(String.format("%02X", b & 0xff));
            }
        }
        return sb.toString();
    }

    private static byte[] signingKey(String secret, String day, String region) {
        byte[] k = hmac(("AWS4" + secret).getBytes(StandardCharsets.UTF_8), day);
        k = hmac(k, region);
        k = hmac(k, "s3");
        return hmac(k, "aws4_request");
    }

    private static byte[] hmac(byte[] key, String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            return mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    private static byte[] sha256(byte[] data) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(data);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String hex(byte[] b) {
        return HexFormat.of().formatHex(b);
    }
}
