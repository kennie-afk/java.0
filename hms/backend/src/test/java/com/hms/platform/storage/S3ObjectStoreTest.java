package com.hms.platform.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

/** The S3 store against (1) AWS's published signing examples, (2) a stub that checks every signature, (3) optionally a real MinIO. */
class S3ObjectStoreTest {

    static final String ACCESS = "AKIAIOSFODNN7EXAMPLE";
    static final String SECRET = "wJalrXUtnFEMI/K7MDENG/bPxRfiCYEXAMPLEKEY";
    static final ObjectLinks LINKS = new ObjectLinks("k".repeat(32).getBytes());
    static final Map<String, byte[]> objects = new ConcurrentHashMap<>();
    static final String endpoint = startStub();

    /** One stub for the JVM, shared with ImagingOnS3Test; it dies with the test run. */
    static String startStub() {
        try {
            HttpServer stub = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            stub.createContext("/", S3ObjectStoreTest::handle);
            stub.start();
            return "http://127.0.0.1:" + stub.getAddress().getPort();
        } catch (java.io.IOException e) {
            throw new IllegalStateException(e);
        }
    }

    /** A stand-in for S3: recomputes the signature itself and refuses anything that does not match, or whose body does not match its hash. */
    static void handle(HttpExchange ex) throws java.io.IOException {
        int status;
        byte[] out = new byte[0];
        try {
            String path = ex.getRequestURI().getRawPath();
            String rawQuery = ex.getRequestURI().getRawQuery();
            byte[] body = ex.getRequestBody().readAllBytes();
            String host = ex.getRequestHeaders().getFirst("Host");
            if (rawQuery != null && rawQuery.contains("X-Amz-Signature=")) {
                // pre-signed GET: rebuild from the query minus the signature
                Map<String, String> q = new TreeMap<>();
                String given = null;
                for (String kv : rawQuery.split("&")) {
                    String[] p = kv.split("=", 2);
                    String k = java.net.URLDecoder.decode(p[0], StandardCharsets.UTF_8);
                    String v = java.net.URLDecoder.decode(p.length > 1 ? p[1] : "", StandardCharsets.UTF_8);
                    if (k.equals("X-Amz-Signature")) {
                        given = v;
                    } else if (!k.startsWith("X-Amz-")) {
                        q.put(k, v);
                    }
                }
                String stamp = rawQuery.replaceAll(".*X-Amz-Date=([0-9TZ]+).*", "$1");
                Instant at = Instant.from(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'").withZone(java.time.ZoneOffset.UTC).parse(stamp));
                long expires = Long.parseLong(rawQuery.replaceAll(".*X-Amz-Expires=([0-9]+).*", "$1"));
                String expected = S3ObjectStore.presignQuery("GET", host, path, q, ACCESS, SECRET, "us-east-1", at, expires);
                if (!expected.endsWith("X-Amz-Signature=" + given)) {
                    status = 403;
                } else if (Instant.now().isAfter(at.plusSeconds(expires))) {
                    status = 403;
                } else {
                    byte[] data = objects.get(path);
                    status = data == null ? 404 : 200;
                    out = data == null ? out : data;
                }
            } else {
                String auth = ex.getRequestHeaders().getFirst("Authorization");
                String signedHeaders = auth.replaceAll(".*SignedHeaders=([^,]+),.*", "$1");
                Map<String, String> headers = new TreeMap<>();
                for (String h : signedHeaders.split(";")) {
                    headers.put(h, ex.getRequestHeaders().getFirst(h));
                }
                String claimed = ex.getRequestHeaders().getFirst("x-amz-content-sha256");
                String expected = S3ObjectStore.authorization(ex.getRequestMethod(), path, rawQuery == null ? "" : rawQuery, headers, claimed, ACCESS, SECRET,
                        "us-east-1", ex.getRequestHeaders().getFirst("x-amz-date"));
                if (!expected.equals(auth)) {
                    status = 403;
                } else if (ex.getRequestMethod().equals("PUT")) {
                    // S3 refuses an upload whose bytes do not hash to what the client declared.
                    boolean same = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(body)).equals(claimed);
                    if (same) {
                        objects.put(path, body);
                    }
                    status = same ? 200 : 400;
                } else {
                    byte[] data = objects.get(path);
                    status = data == null ? 404 : 200;
                    out = data == null ? out : data;
                }
            }
        } catch (Exception e) {
            status = 500;
        }
        ex.sendResponseHeaders(status, out.length == 0 ? -1 : out.length);
        if (out.length > 0) {
            ex.getResponseBody().write(out);
        }
        ex.close();
    }

    private static String key() {
        return UUID.randomUUID() + "/" + UUID.randomUUID();
    }

    @Test
    void signingMatchesAwsPublishedExamples() {
        // "Example: Signature Calculations for Query String / pre-signed URL" in the AWS S3 documentation.
        String q = S3ObjectStore.presignQuery("GET", "examplebucket.s3.amazonaws.com", "/test.txt", Map.of(), ACCESS, SECRET, "us-east-1",
                Instant.parse("2013-05-24T00:00:00Z"), 86400);
        assertThat(q).endsWith("X-Amz-Signature=aeeed9bbccd4d02ee5c0109b86d86835f995330da4c265957d157751f604d404");
        // "Example: GET Object" with a Range header.
        Map<String, String> h = new TreeMap<>();
        h.put("host", "examplebucket.s3.amazonaws.com");
        h.put("range", "bytes=0-9");
        h.put("x-amz-content-sha256", "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855");
        h.put("x-amz-date", "20130524T000000Z");
        assertThat(S3ObjectStore.authorization("GET", "/test.txt", "", h, h.get("x-amz-content-sha256"), ACCESS, SECRET, "us-east-1", "20130524T000000Z"))
                .endsWith("Signature=f0e8bdb87c964420e857bd35b5d6ed310bd44f0170aba48dd91039c6036bdb41");
    }

    private S3ObjectStore store(String secret, String publicEndpoint) {
        return new S3ObjectStore(new S3ObjectStore.Config(endpoint, publicEndpoint, "us-east-1", "hms-images", ACCESS, secret), LINKS);
    }

    @Test
    void putAndReadRoundTripUnderTheTenantPrefix() {
        S3ObjectStore s = store(SECRET, "");
        s.ensureBucket();
        s.ensureBucket();
        assertThat(objects).containsKey("/hms-images");
        String k = key();
        byte[] data = "not-really-a-png".getBytes(StandardCharsets.UTF_8);
        s.put(k, data);
        assertThat(objects).containsKey("/hms-images/" + k);
        assertThat(s.read(k)).contains(data);
        assertThat(s.read(key())).isEmpty();
        assertThat(s.isLocal()).isFalse();
        // Only the expected key shape is ever sent.
        assertThatThrownBy(() -> s.put("../other", data)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> s.read("a/b")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void wrongCredentialsAreRefusedByTheServer() {
        S3ObjectStore bad = store("not-the-secret", "");
        assertThatThrownBy(() -> bad.put(key(), new byte[] {1})).isInstanceOf(IllegalStateException.class).hasMessageContaining("403");
    }

    @Test
    void directLinksAreRealPresignedUrlsAndProxyLinksPointAtTheApi() throws Exception {
        String k = key();
        byte[] data = new byte[] {9, 8, 7};
        store(SECRET, "").put(k, data);
        String url = store(SECRET, endpoint).presignGet(k, "image/png", Duration.ofMinutes(5));
        assertThat(url).startsWith(endpoint + "/hms-images/" + k).contains("response-content-type=image%2Fpng").contains("response-cache-control=no-store");
        HttpClient http = HttpClient.newHttpClient();
        HttpResponse<byte[]> ok = http.send(HttpRequest.newBuilder(URI.create(url)).build(), HttpResponse.BodyHandlers.ofByteArray());
        assertThat(ok.statusCode()).isEqualTo(200);
        assertThat(ok.body()).isEqualTo(data);
        // A tampered signature, or a different object under the same signature, is refused.
        String tampered = url.replaceAll("X-Amz-Signature=(.)", "X-Amz-Signature=" + (url.contains("X-Amz-Signature=a") ? "b" : "a"));
        assertThat(http.send(HttpRequest.newBuilder(URI.create(tampered)).build(), HttpResponse.BodyHandlers.discarding()).statusCode()).isEqualTo(403);
        String other = url.replace(k, key());
        assertThat(http.send(HttpRequest.newBuilder(URI.create(other)).build(), HttpResponse.BodyHandlers.discarding()).statusCode()).isEqualTo(403);

        String proxy = store(SECRET, "").presignGet(k, "image/png", Duration.ofMinutes(5));
        assertThat(proxy).startsWith("/v1/objects/" + k + "?exp=");
    }

    /** Runs the same round trip against a real MinIO: HMS_TEST_S3_ENDPOINT, _BUCKET, _ACCESS_KEY, _SECRET_KEY must be set. */
    @Test
    @EnabledIfEnvironmentVariable(named = "HMS_TEST_S3_ENDPOINT", matches = ".+")
    void realServerAcceptsOurSignatures() throws Exception {
        var env = System.getenv();
        S3ObjectStore s = new S3ObjectStore(new S3ObjectStore.Config(env.get("HMS_TEST_S3_ENDPOINT"), env.get("HMS_TEST_S3_ENDPOINT"), "us-east-1",
                env.get("HMS_TEST_S3_BUCKET"), env.get("HMS_TEST_S3_ACCESS_KEY"), env.get("HMS_TEST_S3_SECRET_KEY")), LINKS);
        s.ensureBucket();
        String k = key();
        byte[] data = new byte[2048];
        new java.util.Random(7).nextBytes(data);
        s.put(k, data);
        assertThat(s.read(k)).contains(data);
        assertThat(s.read(key())).isEmpty();
        String url = s.presignGet(k, "image/png", Duration.ofMinutes(1));
        HttpResponse<byte[]> r = HttpClient.newHttpClient().send(HttpRequest.newBuilder(URI.create(url)).build(), HttpResponse.BodyHandlers.ofByteArray());
        assertThat(r.statusCode()).isEqualTo(200);
        assertThat(r.body()).isEqualTo(data);
        assertThat(r.headers().firstValue("content-type")).contains("image/png");
    }
}
