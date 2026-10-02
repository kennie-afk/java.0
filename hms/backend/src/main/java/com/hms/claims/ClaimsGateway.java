package com.hms.claims;

import java.util.Map;

/**
 * The seam to a payer's claims system (SHA through the DHA eClaims interface). Nothing implements a
 * verified connection: the only implementation, {@link UnverifiedStubGateway}, sends nothing. A real
 * adapter must be written against the current DHA eClaims specification and certified with DHA, and
 * must be marked verified only after that happens.
 */
public interface ClaimsGateway {

    /** What came back. {@code sent} is true only if something actually left HMS for the payer. */
    record Outcome(String adapter, boolean verified, boolean sent, String outcome, String detail) {}

    Outcome submit(String claimNumber, Map<String, Object> bundle);
}
