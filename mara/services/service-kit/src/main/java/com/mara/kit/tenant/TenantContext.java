package com.mara.kit.tenant;

import java.util.function.Supplier;

/**
 * The tenant the current thread is acting for. {@link TenantAwareDataSource} copies it into
 * the session variable row-level security reads, so setting it is what scopes every query
 * that follows; an unset context reads nothing.
 */
public final class TenantContext {

    private static final ThreadLocal<String> CURRENT = new ThreadLocal<>();

    private TenantContext() {
    }

    public static String current() {
        return CURRENT.get();
    }

    public static String require() {
        String tenant = CURRENT.get();
        if (tenant == null) {
            throw new IllegalStateException("no tenant bound to this request");
        }
        return tenant;
    }

    public static void set(String tenantId) {
        CURRENT.set(tenantId);
    }

    public static void clear() {
        CURRENT.remove();
    }

    /** Runs {@code work} as {@code tenantId}, then restores whatever was bound before. */
    public static <T> T with(String tenantId, Supplier<T> work) {
        String previous = CURRENT.get();
        CURRENT.set(tenantId);
        try {
            return work.get();
        } finally {
            if (previous == null) {
                CURRENT.remove();
            } else {
                CURRENT.set(previous);
            }
        }
    }
}
