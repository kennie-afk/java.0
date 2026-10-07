package com.hms.billing;

import com.hms.platform.config.HmsProperties;
import com.hms.platform.security.FixedWindowLimiter;
import jakarta.servlet.http.HttpServletRequest;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Safaricom's confirmation callback. Safaricom sends no token we can verify, so the URL itself is the credential: the path carries a
 * long random secret (HMS_MPESA_CALLBACK_SECRET) compared in constant time. Anything else gets 404, as if the route did not exist.
 * The secret is therefore sensitive wherever URLs are logged: HMS never logs it (see Redact), and the ingress access log must not either.
 */
@RestController
@RequestMapping("/v1/billing/mpesa")
class MpesaCallbackController {

    private static final Map<String, Object> ACK = Map.of("ResultCode", 0, "ResultDesc", "Accepted");

    private final MpesaCallbackService callbacks;
    private final byte[] expected;
    private final boolean trustForwarded;
    /** Wrong guesses per address; a correct secret never counts. */
    private final FixedWindowLimiter guesses = new FixedWindowLimiter(20, 60_000L);

    MpesaCallbackController(MpesaCallbackService callbacks, HmsProperties props, @Value("${hms.mpesa.callback-secret:}") String secret) {
        this.callbacks = callbacks;
        // Hashed first so the comparison is over equal lengths and does not reveal the secret's length.
        this.expected = secret == null || secret.length() < MpesaConfig.MIN_SECRET ? null : sha256(secret);
        this.trustForwarded = props.security().trustForwardedFor();
    }

    @PostMapping(value = "/{secret}/confirmation", consumes = "*/*")
    ResponseEntity<?> confirmation(@PathVariable String secret, @RequestBody(required = false) String body, HttpServletRequest request) {
        boolean ok = expected != null && MessageDigest.isEqual(expected, sha256(secret));
        if (!ok) {
            String from = trustForwarded && request.getHeader("X-Forwarded-For") != null ? request.getHeader("X-Forwarded-For").split(",")[0].trim() : request.getRemoteAddr();
            if (!guesses.hit(from).allowed()) {
                return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).build();
            }
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
        // Any exception from here (a database outage, a lock timeout) becomes a 5xx, which is what makes Safaricom retry.
        callbacks.handle(body == null ? "" : body);
        return ResponseEntity.ok(ACK);
    }

    private static byte[] sha256(String s) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8));
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
