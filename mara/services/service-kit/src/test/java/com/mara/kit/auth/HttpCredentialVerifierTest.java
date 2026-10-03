package com.mara.kit.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.mara.platform.credential.OperatorToken;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class HttpCredentialVerifierTest {

    private HttpServer server;
    private final AtomicInteger calls = new AtomicInteger();
    private final AtomicReference<String> answer = new AtomicReference<>();
    private final AtomicReference<Integer> httpStatus = new AtomicReference<>(200);
    private final AtomicReference<String> authSeen = new AtomicReference<>();

    private String start() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/internal/credentials/verify", ex -> {
            calls.incrementAndGet();
            authSeen.set(ex.getRequestHeaders().getFirst("Authorization"));
            byte[] body = answer.get().getBytes(StandardCharsets.UTF_8);
            ex.sendResponseHeaders(httpStatus.get(), body.length);
            ex.getResponseBody().write(body);
            ex.close();
        });
        server.start();
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    @AfterEach
    void stop() {
        if (server != null) {
            server.stop(0);
        }
    }

    private static final class MutableClock extends Clock {
        Instant now = Instant.parse("2026-10-03T10:00:00Z");

        @Override public java.time.ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(java.time.ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
    }

    private CredentialVerifier.Request req(String token) {
        return new CredentialVerifier.Request(token, "admin:read", "T1", "GET", "/v1/admin/x", "1.2.3.4");
    }

    @Test
    void anOkAnswerIsCachedBrieflyAndPresentsTheServicesOwnCredential() throws Exception {
        var clock = new MutableClock();
        answer.set("{\"ok\":true,\"status\":200,\"reason\":\"ok\",\"credentialId\":\"c1\",\"label\":\"ops\",\"tenantId\":null}");
        var v = new HttpCredentialVerifier(start(), "mop_service", Duration.ofSeconds(15), clock);
        String t = OperatorToken.mint().token();
        var first = v.verify(req(t));
        assertThat(first.ok()).isTrue();
        assertThat(first.tenantId()).isNull();
        assertThat(first.label()).isEqualTo("ops");
        assertThat(authSeen.get()).isEqualTo("Bearer mop_service");
        v.verify(req(t));
        assertThat(calls.get()).as("second answer from cache").isEqualTo(1);
        clock.now = clock.now.plusSeconds(16);
        v.verify(req(t));
        assertThat(calls.get()).as("cache expired: asked again, so a revocation bites within the window").isEqualTo(2);
        // a different scope or tenant is a different question
        v.verify(new CredentialVerifier.Request(t, "admin:write", "T1", "POST", "/v1/admin/x", "1.2.3.4"));
        assertThat(calls.get()).isEqualTo(3);
    }

    @Test
    void aRefusalIsNeverCachedSoANewlyIssuedCredentialWorksAtOnce() throws Exception {
        var clock = new MutableClock();
        answer.set("{\"ok\":false,\"status\":401,\"reason\":\"unknown_credential\"}");
        var v = new HttpCredentialVerifier(start(), "mop_service", Duration.ofSeconds(15), clock);
        String t = OperatorToken.mint().token();
        var denied = v.verify(req(t));
        assertThat(denied.ok()).isFalse();
        assertThat(denied.status()).isEqualTo(401);
        answer.set("{\"ok\":true,\"status\":200,\"reason\":\"ok\",\"credentialId\":\"c\",\"label\":\"l\",\"tenantId\":\"T1\"}");
        var allowed = v.verify(req(t));
        assertThat(allowed.ok()).isTrue();
        assertThat(allowed.tenantId()).isEqualTo("T1");
        assertThat(calls.get()).isEqualTo(2);
    }

    @Test
    void whenIdentityIsDownOrRefusesUsTheAnswerIsAFailClosedRefusal() throws Exception {
        var clock = new MutableClock();
        var v = new HttpCredentialVerifier(start(), "mop_service", Duration.ofSeconds(15), clock);
        // identity rejects our own credential: we cannot vouch for anyone
        answer.set("{\"error\":\"unauthorised\"}");
        httpStatus.set(401);
        var d = v.verify(req(OperatorToken.mint().token()));
        assertThat(d.ok()).isFalse();
        assertThat(d.status()).isEqualTo(503);
        // identity unreachable
        stop();
        var down = new HttpCredentialVerifier("http://127.0.0.1:1", "mop_service", Duration.ofSeconds(15), clock)
                .verify(req(OperatorToken.mint().token()));
        assertThat(down.ok()).isFalse();
        assertThat(down.status()).isEqualTo(503);
    }

    @Test
    void anOutageDoesNotEvictAnAnswerStillInsideItsWindow() throws Exception {
        var clock = new MutableClock();
        answer.set("{\"ok\":true,\"status\":200,\"reason\":\"ok\",\"credentialId\":\"c\",\"label\":\"l\",\"tenantId\":null}");
        var v = new HttpCredentialVerifier(start(), "mop_service", Duration.ofSeconds(15), clock);
        String t = OperatorToken.mint().token();
        assertThat(v.verify(req(t)).ok()).isTrue();
        stop();
        assertThat(v.verify(req(t)).ok()).as("within 15 s the last good answer stands").isTrue();
        clock.now = clock.now.plusSeconds(30);
        assertThat(v.verify(req(t)).ok()).as("beyond the window, with identity gone: refused, never waved through").isFalse();
    }
}
