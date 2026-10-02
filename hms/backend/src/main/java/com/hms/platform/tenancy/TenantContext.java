package com.hms.platform.tenancy;

import java.util.Set;
import java.util.UUID;

/** Who is calling and for which organisation, bound to the request thread for its whole life. */
public final class TenantContext {

    public record Tenant(UUID orgId, UUID practitionerId, Set<UUID> facilityIds, Set<String> permissions) {
        public boolean can(String permission) {
            return permissions.contains(permission);
        }

        /** The caller must work at this facility; being in the organisation is not enough. */
        public void requireFacility(UUID facilityId) {
            if (facilityId == null || !facilityIds.contains(facilityId)) {
                throw com.hms.platform.web.ApiException.forbidden("You do not work at that facility.");
            }
        }
    }

    private static final ThreadLocal<Tenant> CURRENT = new ThreadLocal<>();

    private TenantContext() {}

    public static void set(Tenant tenant) {
        CURRENT.set(tenant);
    }

    public static void clear() {
        CURRENT.remove();
    }

    /** The caller's tenant, or an error: code that needs a tenant must never run without one. */
    public static Tenant require() {
        Tenant tenant = CURRENT.get();
        if (tenant == null) {
            throw new IllegalStateException("no tenant in scope; this ran outside an authenticated request");
        }
        return tenant;
    }

    public static Tenant orNull() {
        return CURRENT.get();
    }
}
