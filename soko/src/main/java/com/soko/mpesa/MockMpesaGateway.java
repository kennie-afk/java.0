package com.soko.mpesa;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * The default gateway -- no Daraja account is required to demo or test order
 * payment. Amount 1 (one shilling) deliberately simulates the insufficient-
 * funds path so that failure handling is provable without a real sandbox.
 */
@Component
@ConditionalOnProperty(name = "soko.mpesa.mode", havingValue = "mock", matchIfMissing = true)
public class MockMpesaGateway implements MpesaGateway {

    private static final Logger log = LoggerFactory.getLogger(MockMpesaGateway.class);
    private static final BigDecimal INSUFFICIENT_FUNDS_TRIGGER = new BigDecimal("1");

    private final List<StkPushRequest> requests = new CopyOnWriteArrayList<>();

    /**
     * Demo convenience, off unless SOKO_MPESA_MOCK_AUTOCOMPLETE_SECONDS is a positive number:
     * a real phone answers the PIN prompt by itself, so in a demo the mock "customer" does the
     * same after a short delay by POSTing a genuine success callback to our own public callback
     * endpoint. It goes through exactly the code path Safaricom's callback would.
     */
    private final int autocompleteSeconds = parseSeconds(System.getenv("SOKO_MPESA_MOCK_AUTOCOMPLETE_SECONDS"));
    private final ScheduledExecutorService scheduler = autocompleteSeconds > 0
            ? Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "mock-mpesa-customer");
                t.setDaemon(true);
                return t;
            })
            : null;
    private final HttpClient http = autocompleteSeconds > 0 ? HttpClient.newHttpClient() : null;

    private static int parseSeconds(String raw) {
        try {
            return raw == null ? 0 : Math.max(0, Integer.parseInt(raw.trim()));
        } catch (NumberFormatException ex) {
            return 0;
        }
    }

    private void answerPrompt(StkPushRequest request, String merchant, String checkout) {
        String receipt = "MOCK" + UUID.randomUUID().toString().replace("-", "").substring(0, 6).toUpperCase();
        String body = "{\"Body\":{\"stkCallback\":{\"MerchantRequestID\":\"" + merchant
                + "\",\"CheckoutRequestID\":\"" + checkout
                + "\",\"ResultCode\":0,\"ResultDesc\":\"The service request is processed successfully.\","
                + "\"CallbackMetadata\":{\"Item\":[{\"Name\":\"Amount\",\"Value\":" + request.amount()
                + "},{\"Name\":\"MpesaReceiptNumber\",\"Value\":\"" + receipt
                + "\"},{\"Name\":\"PhoneNumber\",\"Value\":\"" + request.phoneNumber() + "\"}]}}}}";
        try {
            http.send(HttpRequest.newBuilder(URI.create(request.callbackUrl()))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.discarding());
        } catch (Exception ex) {
            log.warn("[mock M-Pesa] customer could not reach the callback endpoint: {}", ex.getMessage());
        }
    }

    @Override
    public StkPushResponse stkPush(StkPushRequest request) {
        requests.add(request);
        log.info("[mock M-Pesa] STK push of {} to {}", request.amount(), request.phoneNumber());

        if (request.amount().compareTo(INSUFFICIENT_FUNDS_TRIGGER) == 0) {
            return new StkPushResponse(false, null, null, "1",
                    "The balance is insufficient for the transaction", null);
        }

        String merchant = "ws_CO_" + UUID.randomUUID().toString().substring(0, 12);
        String checkout = "ws_CO_" + UUID.randomUUID().toString().substring(0, 12);
        if (scheduler != null) {
            scheduler.schedule(() -> answerPrompt(request, merchant, checkout), autocompleteSeconds, TimeUnit.SECONDS);
        }
        return new StkPushResponse(true, merchant, checkout, "0",
                "Success. Request accepted for processing",
                "Enter your M-PESA PIN to complete the payment");
    }

    @Override
    public boolean verifyCallbackSignature(String rawBody, String signature) {
        return true;
    }

    public List<StkPushRequest> requests() {
        return List.copyOf(requests);
    }
}
