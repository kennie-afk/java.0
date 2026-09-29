package com.soko.mpesa;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
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
