package com.soko.security.tenant;

import com.soko.security.Principal;
import java.util.UUID;
import java.util.function.Supplier;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Who the database should believe the current transaction is working for.
 *
 * <p>Normally that is the tenant on the caller's signed token. The two exceptions are explicit
 * and scoped to a block of code, so reading a call site tells you where isolation is widened:
 *
 * <ul>
 *   <li>{@link #runAs(UUID, Supplier)}: work done for one named tenant with no caller (the
 *       public storefront, a one-time-code check, a scheduled bill for one tenant);</li>
 *   <li>{@link #asSystem(Supplier)}: the few operations that cannot know the tenant until they
 *       have looked something up (sign-in by e-mail, the M-Pesa callback by checkout id).</li>
 * </ul>
 *
 * <p>The binding is applied when a transaction begins ({@link TenantAwareDataSource}), so a
 * scope must enclose the outermost transactional call, not sit inside it.
 */
public final class TenantBinding {

    private record Scope(UUID tenant, boolean system) {
    }

    private static final ThreadLocal<Scope> SCOPE = new ThreadLocal<>();

    private TenantBinding() {
    }

    public static <T> T runAs(UUID tenantId, Supplier<T> work) {
        return within(new Scope(tenantId, false), work);
    }

    public static void runAs(UUID tenantId, Runnable work) {
        runAs(tenantId, () -> {
            work.run();
            return null;
        });
    }

    public static <T> T asSystem(Supplier<T> work) {
        return within(new Scope(null, true), work);
    }

    public static void asSystem(Runnable work) {
        asSystem(() -> {
            work.run();
            return null;
        });
    }

    private static <T> T within(Scope scope, Supplier<T> work) {
        Scope previous = SCOPE.get();
        SCOPE.set(scope);
        try {
            return work.get();
        } finally {
            restore(previous);
        }
    }

    private static void restore(Scope previous) {
        if (previous == null) {
            SCOPE.remove();
        } else {
            SCOPE.set(previous);
        }
    }

    /** The tenant to bind: an explicit scope wins, otherwise the signed-in caller's, otherwise none. */
    static String tenant() {
        Scope scope = SCOPE.get();
        if (scope != null && scope.tenant() != null) {
            return scope.tenant().toString();
        }
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof Principal principal && principal.tenantId() != null) {
            return principal.tenantId().toString();
        }
        return "";
    }

    static boolean system() {
        Scope scope = SCOPE.get();
        return scope != null && scope.system();
    }
}
