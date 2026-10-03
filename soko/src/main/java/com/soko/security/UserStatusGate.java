package com.soko.security;

import com.soko.domain.AppUser;
import com.soko.persistence.UserRepository;
import com.soko.security.tenant.TenantBinding;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Decides whether the account behind a still-valid token may still act.
 *
 * <p>A signed token cannot be recalled, so a suspended account would otherwise keep working until
 * the token expires (12 hours by default). The filter asks this gate on every request instead. The
 * answer is cached for a few seconds so the database is not hit per request: a suspension made on
 * this replica takes effect at once ({@link #forget}), on another replica within the cache window.
 * An account that no longer exists counts as inactive.
 */
@Component
public class UserStatusGate {

    private record Entry(boolean active, long expiresAtNanos) {
    }

    private final UserRepository users;
    private final long ttlNanos;
    private final ConcurrentHashMap<UUID, Entry> cache = new ConcurrentHashMap<>();

    public UserStatusGate(
            UserRepository users, @Value("${soko.auth.status-cache-seconds:5}") long cacheSeconds) {
        this.users = users;
        this.ttlNanos = Duration.ofSeconds(Math.max(0, cacheSeconds)).toNanos();
    }

    public boolean isActive(Principal principal) {
        long now = System.nanoTime();
        Entry cached = cache.get(principal.userId());
        if (cached != null && now < cached.expiresAtNanos()) {
            return cached.active();
        }
        boolean active = TenantBinding.runAs(principal.tenantId(), () ->
                users.findById(principal.userId())
                        .filter(user -> user.getTenantId().equals(principal.tenantId()))
                        .map(AppUser::getStatus)
                        .map("ACTIVE"::equals)
                        .orElse(false));
        cache.put(principal.userId(), new Entry(active, now + ttlNanos));
        return active;
    }

    /** Called right after a status change so this replica never serves a stale answer. */
    public void forget(UUID userId) {
        cache.remove(userId);
    }
}
