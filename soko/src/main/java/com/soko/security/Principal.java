package com.soko.security;

import java.util.UUID;

public record Principal(
        UUID userId, UUID tenantId, String email, String role, UUID supplierId, UUID customerId) {

    public Principal(UUID userId, UUID tenantId, String email, String role) {
        this(userId, tenantId, email, role, null, null);
    }
}
