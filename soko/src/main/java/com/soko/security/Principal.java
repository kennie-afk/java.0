package com.soko.security;

import java.util.UUID;

/** {@code tokenVersion} is the account's token version when the token was issued; a password reset bumps it. */
public record Principal(
        UUID userId, UUID tenantId, String email, String role, UUID supplierId, UUID customerId,
        int tokenVersion) {

    public Principal(UUID userId, UUID tenantId, String email, String role, UUID supplierId, UUID customerId) {
        this(userId, tenantId, email, role, supplierId, customerId, 0);
    }

    public Principal(UUID userId, UUID tenantId, String email, String role) {
        this(userId, tenantId, email, role, null, null, 0);
    }
}
