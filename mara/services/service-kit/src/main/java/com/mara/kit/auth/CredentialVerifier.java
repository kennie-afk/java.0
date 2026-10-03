package com.mara.kit.auth;

/**
 * Decides whether a presented operator or service credential may make one request. In
 * identity-service the answer comes from its own database; in the other services it comes from
 * identity-service over HTTP ({@link HttpCredentialVerifier}). The same filter sits in front of
 * both, so a rule is written once.
 */
public interface CredentialVerifier {

    /** Everything the decision (and its audit trail) needs about the request. */
    record Request(String presented, String requiredScope, String tenantHeader, String method, String path,
                   String remoteAddr) {
    }

    /**
     * {@code status} is the HTTP status to answer with when {@code ok} is false: 401 for a
     * credential that is not valid, 403 for a valid one that may not do this, 503 when the
     * decision could not be made (and the request is refused, never waved through).
     * {@code tenantId} is the credential's own tenant for a tenant-bound credential, else null.
     */
    record Decision(boolean ok, int status, String reason, String credentialId, String label, String tenantId) {
        public static Decision allowed(String credentialId, String label, String tenantId) {
            return new Decision(true, 200, "ok", credentialId, label, tenantId);
        }

        public static Decision denied(int status, String reason) {
            return new Decision(false, status, reason, null, null, null);
        }
    }

    Decision verify(Request request);
}
