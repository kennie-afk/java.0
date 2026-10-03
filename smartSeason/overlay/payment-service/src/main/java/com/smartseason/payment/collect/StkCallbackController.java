package com.smartseason.payment.collect;

import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Where Safaricom calls back. It is permitted without a bearer token on purpose (see the public
 * matchers in tools/generate.py): Daraja cannot present a SmartSeason token.
 *
 * <p>Authenticity: the callback URL is the one thing we hand Safaricom ourselves, so in live mode a
 * secret rides in it ({@code ?secret=...}, added by the gateway). The same secret is also accepted
 * in an {@code X-Mpesa-Signature} header. Nothing here assumes Daraja signs its callbacks. The
 * payment is correlated by {@code CheckoutRequestID}, which {@link StkCallbackService} treats as the
 * idempotency key, so a callback delivered twice is safe.
 *
 * <p>Answers: 200 with the acknowledgement Daraja expects whenever the callback was understood
 * (including a replay or a checkout we do not know, so Safaricom stops retrying), 401 for a wrong
 * secret, 400 for a body that cannot be read.
 */
@RestController
@RequestMapping("/api/payment/v1/mpesa")
public class StkCallbackController {

    private static final Map<String, Object> ACCEPTED = Map.of("ResultCode", 0, "ResultDesc", "Accepted");

    private final StkCallbackService service;

    public StkCallbackController(StkCallbackService service) {
        this.service = service;
    }

    @PostMapping("/stk-callback")
    public ResponseEntity<Map<String, Object>> callback(
            @RequestBody String rawBody,
            @RequestHeader(name = "X-Mpesa-Signature", required = false) String signature,
            @RequestParam(name = "secret", required = false) String secret) {
        String presented = secret != null ? secret : signature;
        return switch (service.handle(rawBody, presented)) {
            case PROCESSED, DUPLICATE, UNKNOWN_TRANSACTION -> ResponseEntity.ok(ACCEPTED);
            case INVALID_SIGNATURE -> ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("ResultCode", 1, "ResultDesc", "rejected"));
            case MALFORMED -> ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("ResultCode", 1, "ResultDesc", "malformed body"));
        };
    }
}
