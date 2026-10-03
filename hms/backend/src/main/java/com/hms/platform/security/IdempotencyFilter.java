package com.hms.platform.security;

import com.hms.platform.tenancy.TenantContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.ByteArrayInputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Pattern;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingResponseWrapper;

/**
 * Carries out a write once per Idempotency-Key. Runs after the token filter, so the key is scoped to the signed-in person
 * and their organisation. The first request claims the key; a repeat with the same body gets the stored answer back (with
 * Idempotent-Replay: true); the same key with a different body is refused; a repeat that arrives while the first is still
 * running is told to wait. A server error releases the key so the client can try again.
 */
@Component
class IdempotencyFilter extends OncePerRequestFilter {

    private static final Pattern KEY = Pattern.compile("^[A-Za-z0-9_-]{16,80}$");
    private static final Set<String> WRITES = Set.of("POST", "PUT", "PATCH", "DELETE");
    private static final int MAX_BODY = 1_048_576;
    /** A claim older than this with no answer is taken to belong to a crashed request and may be taken over. */
    private static final String STALE = "2 minutes";

    private final JdbcClient jdbc;
    private final TransactionTemplate tx;

    IdempotencyFilter(JdbcClient jdbc, PlatformTransactionManager txm) {
        this.jdbc = jdbc;
        this.tx = new TransactionTemplate(txm);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !WRITES.contains(request.getMethod()) || request.getHeader("Idempotency-Key") == null || !request.getRequestURI().startsWith("/v1/");
    }

    private record Existing(byte[] hash, String state, Integer status, String contentType, byte[] body) {}

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain) throws ServletException, IOException {
        TenantContext.Tenant tenant = TenantContext.orNull();
        if (tenant == null) {
            chain.doFilter(request, response);
            return;
        }
        String key = request.getHeader("Idempotency-Key").trim();
        if (!KEY.matcher(key).matches()) {
            problem(response, 400, "idempotency_key_invalid", "The Idempotency-Key must be 16 to 80 letters, digits, - or _.");
            return;
        }
        byte[] body = request.getInputStream().readNBytes(MAX_BODY + 1);
        if (body.length > MAX_BODY) {
            problem(response, 413, "payload_too_large", "That request is too large.");
            return;
        }
        byte[] hash = digest(request.getMethod() + " " + request.getRequestURI() + "?" + (request.getQueryString() == null ? "" : request.getQueryString()), body);
        UUID org = tenant.orgId();
        UUID actor = tenant.practitionerId();

        Boolean claimed = tx.execute(s -> {
            if (ThreadLocalRandom.current().nextInt(100) == 0) {
                jdbc.sql("DELETE FROM idempotency_keys WHERE org_id = ? AND created_at < now() - interval '7 days'").param(org).update();
            }
            int inserted = jdbc.sql("INSERT INTO idempotency_keys (org_id, actor_id, key, method, path, request_hash) VALUES (?, ?, ?, ?, ?, ?) ON CONFLICT DO NOTHING")
                    .params(org, actor, key, request.getMethod(), request.getRequestURI(), hash).update();
            if (inserted == 1) {
                return true;
            }
            // Take over a claim whose request evidently died, so a crash cannot lock a key for ever.
            int taken = jdbc.sql("UPDATE idempotency_keys SET created_at = now(), request_hash = ? WHERE org_id = ? AND actor_id = ? AND key = ? AND state = 'IN_PROGRESS' AND created_at < now() - interval '" + STALE + "'")
                    .params(hash, org, actor, key).update();
            return taken == 1;
        });
        if (Boolean.FALSE.equals(claimed)) {
            Optional<Existing> existing = tx.execute(s -> jdbc.sql("SELECT request_hash, state, status, content_type, response_body FROM idempotency_keys WHERE org_id = ? AND actor_id = ? AND key = ?")
                    .params(org, actor, key).query((rs, n) -> new Existing(rs.getBytes("request_hash"), rs.getString("state"), (Integer) rs.getObject("status"), rs.getString("content_type"), rs.getBytes("response_body"))).optional());
            if (existing == null || existing.isEmpty()) {
                problem(response, 409, "request_in_progress", "That request is still being processed. Try again in a moment.");
                return;
            }
            Existing e = existing.get();
            if (!MessageDigest.isEqual(e.hash(), hash)) {
                problem(response, 422, "idempotency_key_reused", "That Idempotency-Key was already used for a different request.");
                return;
            }
            if (!"DONE".equals(e.state()) || e.status() == null) {
                response.setHeader("Retry-After", "1");
                problem(response, 409, "request_in_progress", "That request is still being processed. Try again in a moment.");
                return;
            }
            response.setStatus(e.status());
            response.setHeader("Idempotent-Replay", "true");
            if (e.contentType() != null) {
                response.setContentType(e.contentType());
            }
            if (e.body() != null) {
                response.getOutputStream().write(e.body());
            }
            return;
        }

        ContentCachingResponseWrapper wrapped = new ContentCachingResponseWrapper(response);
        boolean finished = false;
        try {
            chain.doFilter(new CachedBody(request, body), wrapped);
            finished = true;
        } finally {
            int status = wrapped.getStatus();
            // A refusal for lack of rights (401/403) or a rate limit is not an answer to keep: the next try may be allowed.
            if (finished && status < 500 && status != 401 && status != 403 && status != 429) {
                byte[] out = wrapped.getContentAsByteArray();
                tx.executeWithoutResult(s -> jdbc.sql("UPDATE idempotency_keys SET state = 'DONE', status = ?, content_type = ?, response_body = ? WHERE org_id = ? AND actor_id = ? AND key = ?")
                        .params(status, wrapped.getContentType(), out, org, actor, key).update());
            } else {
                tx.executeWithoutResult(s -> jdbc.sql("DELETE FROM idempotency_keys WHERE org_id = ? AND actor_id = ? AND key = ?").params(org, actor, key).update());
            }
            wrapped.copyBodyToResponse();
        }
    }

    private static void problem(HttpServletResponse response, int status, String code, String detail) throws IOException {
        response.setStatus(status);
        response.setContentType("application/problem+json");
        response.getWriter().write("{\"title\":\"" + code + "\",\"status\":" + status + ",\"code\":\"" + code + "\",\"detail\":\"" + detail + "\"}");
    }

    private static byte[] digest(String head, byte[] body) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            md.update(head.getBytes(StandardCharsets.UTF_8));
            md.update((byte) 0);
            return md.digest(body);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    /** The body was read to hash it, so the controller is given a fresh stream over the same bytes. */
    private static final class CachedBody extends HttpServletRequestWrapper {
        private final byte[] body;

        CachedBody(HttpServletRequest request, byte[] body) {
            super(request);
            this.body = body;
        }

        @Override
        public ServletInputStream getInputStream() {
            ByteArrayInputStream in = new ByteArrayInputStream(body);
            return new ServletInputStream() {
                @Override
                public int read() {
                    return in.read();
                }

                @Override
                public boolean isFinished() {
                    return in.available() == 0;
                }

                @Override
                public boolean isReady() {
                    return true;
                }

                @Override
                public void setReadListener(ReadListener listener) {
                    throw new UnsupportedOperationException();
                }
            };
        }

        @Override
        public BufferedReader getReader() {
            return new BufferedReader(new InputStreamReader(getInputStream(), StandardCharsets.UTF_8));
        }
    }
}
