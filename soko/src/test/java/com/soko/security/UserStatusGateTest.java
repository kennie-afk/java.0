package com.soko.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.soko.domain.AppUser;
import com.soko.persistence.UserRepository;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class UserStatusGateTest {

    private final UUID tenant = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();
    private final Principal principal = new Principal(userId, tenant, "a@b.test", "OPERATOR", null, null);

    private AppUser user(String status, UUID tenantId) {
        AppUser u = new AppUser();
        u.setId(userId);
        u.setTenantId(tenantId);
        u.setStatus(status);
        return u;
    }

    @Test
    void anActiveAccountPassesAndIsCachedForTheWindow() {
        UserRepository users = mock(UserRepository.class);
        when(users.findById(userId)).thenReturn(Optional.of(user("ACTIVE", tenant)));
        UserStatusGate gate = new UserStatusGate(users, 60);

        assertThat(gate.isActive(principal)).isTrue();
        assertThat(gate.isActive(principal)).isTrue();
        verify(users, times(1)).findById(userId);
    }

    @Test
    void aSuspendedAccountIsRefusedEvenWithAValidToken() {
        UserRepository users = mock(UserRepository.class);
        when(users.findById(userId)).thenReturn(Optional.of(user("SUSPENDED", tenant)));

        assertThat(new UserStatusGate(users, 60).isActive(principal)).isFalse();
    }

    @Test
    void forgettingTakesEffectAtOnceOnThisReplicaDespiteTheCache() {
        UserRepository users = mock(UserRepository.class);
        AppUser stored = user("ACTIVE", tenant);
        when(users.findById(userId)).thenReturn(Optional.of(stored));
        UserStatusGate gate = new UserStatusGate(users, 60);
        assertThat(gate.isActive(principal)).isTrue();

        stored.setStatus("SUSPENDED");
        gate.forget(userId);

        assertThat(gate.isActive(principal)).isFalse();
    }

    @Test
    void anAccountThatNoLongerExistsOrBelongsToAnotherTenantIsRefused() {
        UserRepository users = mock(UserRepository.class);
        when(users.findById(userId)).thenReturn(Optional.empty());
        assertThat(new UserStatusGate(users, 0).isActive(principal)).isFalse();

        when(users.findById(userId)).thenReturn(Optional.of(user("ACTIVE", UUID.randomUUID())));
        assertThat(new UserStatusGate(users, 0).isActive(principal)).isFalse();
    }
}
