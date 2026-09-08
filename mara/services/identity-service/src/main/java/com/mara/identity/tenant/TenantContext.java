package com.mara.identity.tenant;

/**
 * The tenant the current request belongs to, as asserted by the gateway.
 *
 * <p>Held in a thread local rather than passed as a parameter because it has to reach
 * the {@code DataSource} — which sits far below any code that knows about HTTP — to set
 * the session variable the row-level security policies read.
 *
 * <p>Deliberately has no setter for arbitrary code to call. Only {@link TenantFilter}
 * populates it, from a claim the gateway signed, and only that filter clears it. A
 * service that could set its own tenant would make the whole isolation model advisory.
 */
public final class TenantContext {

    private static final ThreadLocal<String> CURRENT = new ThreadLocal<>();

    private TenantContext() {
    }

    /** The tenant for this request, or null when the request is not tenant-scoped. */
    public static String current() {
        return CURRENT.get();
    }

    public static boolean isSet() {
        return CURRENT.get() != null;
    }

    /**
     * Runs {@code work} with the given tenant bound, restoring whatever was bound
     * before.
     *
     * <p>Scoped rather than set-and-forget: a thread returned to the pool still holding
     * a tenant is how one shop's request ends up reading another's data. Restoring the
     * previous value rather than clearing keeps nesting honest.
     */
    static <T> T with(String tenantId, java.util.function.Supplier<T> work) {
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

    static void clear() {
        CURRENT.remove();
    }

    static void set(String tenantId) {
        CURRENT.set(tenantId);
    }
}
