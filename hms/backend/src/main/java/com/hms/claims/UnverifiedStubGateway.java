package com.hms.claims;

import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * UNVERIFIED STUB. Records that a submission was requested and sends nothing anywhere. It exists so
 * the rest of the claims workflow (assembly, validation, status, audit) can be built and exercised
 * before DHA credentials, the eClaims specification and certification exist. It must never be
 * described as a live SHA/DHA connection.
 */
@Component
class UnverifiedStubGateway implements ClaimsGateway {

    @Override
    public Outcome submit(String claimNumber, Map<String, Object> bundle) {
        return new Outcome("unverified-stub", false, false, "NOT_SENT",
                "Stub adapter: nothing was transmitted. No live SHA/DHA connection is configured or verified.");
    }
}
