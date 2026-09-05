package com.smartseason.marketplace.platform;

import java.util.Optional;
import java.util.UUID;

public final class TenantContext {

    private static final ThreadLocal<UUID> CURRENT = new ThreadLocal<>();

    private TenantContext() {
    }

    public static void set(UUID tenantId) {
        CURRENT.set(tenantId);
    }

    public static Optional<UUID> tenantId() {
        return Optional.ofNullable(CURRENT.get());
    }

    public static UUID requireTenantId() {
        UUID tenantId = CURRENT.get();
        if (tenantId == null) {
            throw new TenantMissingException();
        }
        return tenantId;
    }

    public static void clear() {
        CURRENT.remove();
    }
}
