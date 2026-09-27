package com.soko;

import static org.assertj.core.api.Assertions.assertThat;

import com.soko.domain.AppUser;
import com.soko.notifications.EmailNotifier;
import com.soko.notifications.OrderNotifications;
import com.soko.persistence.UserRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class OrderNotificationsTest {

    private record Sent(String to, String subject, String body) {}

    private static AppUser user(UUID tenantId, String email, String role, UUID customerId) {
        AppUser user = new AppUser();
        user.setTenantId(tenantId);
        user.setEmail(email);
        user.setFullName(email);
        user.setPasswordHash("x");
        user.setRole(role);
        user.setCustomerId(customerId);
        return user;
    }

    /** An in-memory stand-in, so this test needs no Spring context or real database. */
    private static final class FakeUsers {
        private final List<AppUser> all = new ArrayList<>();

        void add(AppUser user) {
            all.add(user);
        }

        List<AppUser> byTenantAndCustomer(UUID tenantId, UUID customerId) {
            return all.stream()
                    .filter(u -> u.getTenantId().equals(tenantId) && customerId.equals(u.getCustomerId()))
                    .toList();
        }

        List<AppUser> byTenantAndRole(UUID tenantId, String role) {
            return all.stream()
                    .filter(u -> u.getTenantId().equals(tenantId) && role.equals(u.getRole()))
                    .toList();
        }
    }

    @Test
    void anOrderNotifiesBothTheBuyerAndEveryOwner() {
        UUID tenantId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();

        FakeUsers fake = new FakeUsers();
        fake.add(user(tenantId, "buyer@example.com", "CUSTOMER", customerId));
        fake.add(user(tenantId, "owner@example.com", "OWNER", null));
        fake.add(user(UUID.randomUUID(), "other-tenant-owner@example.com", "OWNER", null));

        List<Sent> sent = new ArrayList<>();
        EmailNotifier recorder = (to, subject, body) -> sent.add(new Sent(to, subject, body));

        OrderNotifications notifications =
                new OrderNotifications(adapt(fake), recorder);

        notifications.orderPlaced(tenantId, customerId, "SO-ABC123", 150000);

        assertThat(sent).hasSize(2);
        assertThat(sent).anySatisfy(s -> {
            assertThat(s.to()).isEqualTo("buyer@example.com");
            assertThat(s.subject()).contains("SO-ABC123");
            assertThat(s.body()).contains("KSh 1,500");
        });
        assertThat(sent).anySatisfy(s -> assertThat(s.to()).isEqualTo("owner@example.com"));
        assertThat(sent).noneMatch(s -> s.to().equals("other-tenant-owner@example.com"));
    }

    @Test
    void aDispatchNotifiesOnlyTheBuyer() {
        UUID tenantId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();

        FakeUsers fake = new FakeUsers();
        fake.add(user(tenantId, "buyer@example.com", "CUSTOMER", customerId));
        fake.add(user(tenantId, "owner@example.com", "OWNER", null));

        List<Sent> sent = new ArrayList<>();
        EmailNotifier recorder = (to, subject, body) -> sent.add(new Sent(to, subject, body));
        OrderNotifications notifications = new OrderNotifications(adapt(fake), recorder);

        notifications.lineDispatched(tenantId, customerId, "SO-ABC123", "Fermented milk 500ml");

        assertThat(sent).hasSize(1);
        assertThat(sent.get(0).to()).isEqualTo("buyer@example.com");
        assertThat(sent.get(0).body()).contains("dispatched");
    }

    @Test
    void aFailingNotifierNeverPropagates() {
        UUID tenantId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();

        FakeUsers fake = new FakeUsers();
        fake.add(user(tenantId, "buyer@example.com", "CUSTOMER", customerId));

        EmailNotifier broken = (to, subject, body) -> {
            throw new RuntimeException("mail provider unreachable");
        };
        OrderNotifications notifications = new OrderNotifications(adapt(fake), broken);

        // Placing an order (or dispatching a line) must never fail because the
        // courtesy email about it could not be sent.
        notifications.orderPlaced(tenantId, customerId, "SO-ABC123", 150000);
    }

    /**
     * OrderNotifications depends on the real UserRepository (a Spring Data JPA
     * interface), so this adapts the in-memory fake to that exact surface
     * without needing a database or a mocking framework.
     */
    private static UserRepository adapt(FakeUsers fake) {
        return (UserRepository)
                java.lang.reflect.Proxy.newProxyInstance(
                        UserRepository.class.getClassLoader(),
                        new Class<?>[] {UserRepository.class},
                        (proxy, method, args) -> {
                            switch (method.getName()) {
                                case "findByTenantIdAndCustomerId":
                                    return fake.byTenantAndCustomer((UUID) args[0], (UUID) args[1]);
                                case "findByTenantIdAndRole":
                                    return fake.byTenantAndRole((UUID) args[0], (String) args[1]);
                                default:
                                    throw new UnsupportedOperationException(method.getName());
                            }
                        });
    }
}
