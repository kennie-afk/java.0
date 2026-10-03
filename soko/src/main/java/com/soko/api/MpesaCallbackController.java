package com.soko.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.soko.mpesa.StkCallback;
import com.soko.payment.PaymentService;
import com.soko.payment.PaymentCallbackHandler;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Where Safaricom calls back, not a customer or console. Permitted
 * unauthenticated on purpose (see {@code /v1/public/**} in SecurityConfig) --
 * Daraja cannot present a Soko bearer token. Correlation is entirely by
 * {@code CheckoutRequestID}, which {@link PaymentService} treats as the
 * idempotency key, so a retried callback is safe to receive twice.
 *
 * <p>Authenticity: the callback URL is the one thing we hand Safaricom ourselves, so in live
 * mode a secret rides in it ({@code ?secret=...}, added by the gateway). The same secret is also
 * accepted in an {@code X-Mpesa-Signature} header. Nothing here assumes Daraja signs its
 * callbacks.
 */
@RestController
@RequestMapping("/v1/public/mpesa")
public class MpesaCallbackController {

    private static final Logger log = LoggerFactory.getLogger(MpesaCallbackController.class);

    private final PaymentCallbackHandler handler;
    private final ObjectMapper objectMapper;

    public MpesaCallbackController(PaymentCallbackHandler handler, ObjectMapper objectMapper) {
        this.handler = handler;
        this.objectMapper = objectMapper;
    }

    @PostMapping("/stk-callback")
    public ResponseEntity<Map<String, Object>> callback(
            @RequestBody String rawBody,
            @RequestHeader(name = "X-Mpesa-Signature", required = false) String signature,
            @RequestParam(name = "secret", required = false) String secret) {
        StkCallback parsed;
        try {
            parsed = objectMapper.readValue(rawBody, StkCallback.class);
        } catch (Exception ex) {
            log.warn("unparseable M-Pesa callback body");
            return ResponseEntity.ok(Map.of("ResultCode", 1, "ResultDesc", "malformed body"));
        }

        // Applying the callback and settling the order/invoice are one transaction (see
        // PaymentCallbackHandler), so a payment is never recorded without being applied.
        handler.handle(parsed, rawBody, signature != null ? signature : secret);

        // Daraja only cares that we returned 200 with ResultCode 0; it is not
        // shown to a human and does not reflect whether the PAYMENT itself
        // succeeded, only that the callback was received and processed.
        return ResponseEntity.ok(Map.of("ResultCode", 0, "ResultDesc", "Accepted"));
    }
}
