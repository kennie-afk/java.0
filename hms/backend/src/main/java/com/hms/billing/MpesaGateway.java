package com.hms.billing;

import java.math.BigDecimal;

/**
 * The seam to M-Pesa. The default implementation is a MOCK that never touches a network. A live
 * Daraja implementation has NOT been written or verified: it needs Safaricom credentials, a
 * registered shortcode, a public HTTPS callback URL and the current API documentation. Until then
 * HMS_MPESA_MODE=live fails loudly rather than pretending.
 */
public interface MpesaGateway {

    /** What the gateway returns when asked to push a payment prompt to a phone. */
    record StkRequest(String checkoutRequestId, String note) {}

    StkRequest initiate(String phoneE164, BigDecimal amount, String accountReference, String description);

    /** True for the mock, which the API uses to allow simulated completion. */
    boolean isMock();
}
