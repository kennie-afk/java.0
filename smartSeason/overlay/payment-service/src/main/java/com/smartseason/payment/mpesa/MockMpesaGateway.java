package com.smartseason.payment.mpesa;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "smartseason.mpesa.mode", havingValue = "mock", matchIfMissing = true)
public class MockMpesaGateway implements MpesaGateway {

    private static final Logger log = LoggerFactory.getLogger(MockMpesaGateway.class);
    private static final BigDecimal INSUFFICIENT_FUNDS_TRIGGER = new BigDecimal("1");

    private final List<StkPushRequest> stkRequests = new CopyOnWriteArrayList<>();
    private final List<B2cRequest> b2cRequests = new CopyOnWriteArrayList<>();

    @Override
    public StkPushResponse stkPush(StkPushRequest request) {
        stkRequests.add(request);
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
    public B2cResponse businessToCustomer(B2cRequest request) {
        b2cRequests.add(request);
        log.info("[mock M-Pesa] B2C of {} to {}", request.amount(), request.phoneNumber());

        return new B2cResponse(true,
                "AG_" + UUID.randomUUID().toString().substring(0, 12),
                "OR_" + UUID.randomUUID().toString().substring(0, 12),
                "0", "Accept the service request successfully.");
    }

    @Override
    public boolean verifyCallbackSignature(String rawBody, String signature) {
        return true;
    }

    public List<StkPushRequest> stkRequests() {
        return List.copyOf(stkRequests);
    }

    public List<B2cRequest> b2cRequests() {
        return List.copyOf(b2cRequests);
    }

    public void reset() {
        stkRequests.clear();
        b2cRequests.clear();
    }
}
