package com.soko.billing;

import static org.assertj.core.api.Assertions.assertThat;

import com.soko.domain.Subscription;
import com.soko.persistence.SubscriptionRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

class SubscriptionServiceTest {

    /** Enforces the same "one active row per tenant" invariant the partial unique index does. */
    private static final class FakeRepo {
        private final List<Subscription> all = new ArrayList<>();

        Optional<Subscription> active(UUID tenantId) {
            return all.stream()
                    .filter(s -> s.getTenantId().equals(tenantId) && s.getEndedAt() == null)
                    .findFirst();
        }

        Subscription save(Subscription s) {
            if (s.getId() == null) {
                if (s.getEndedAt() == null && active(s.getTenantId()).isPresent()) {
                    throw new DataIntegrityViolationException("one active subscription per tenant");
                }
                s.setId(UUID.randomUUID());
                all.add(s);
            }
            return s;
        }
    }

    private static SubscriptionRepository adapt(FakeRepo fake) {
        return (SubscriptionRepository)
                java.lang.reflect.Proxy.newProxyInstance(
                        SubscriptionRepository.class.getClassLoader(),
                        new Class<?>[] {SubscriptionRepository.class},
                        (proxy, method, args) -> {
                            switch (method.getName()) {
                                case "findByTenantIdAndEndedAtIsNull":
                                    return fake.active((UUID) args[0]);
                                case "save":
                                case "saveAndFlush":
                                    return fake.save((Subscription) args[0]);
                                default:
                                    throw new UnsupportedOperationException(method.getName());
                            }
                        });
    }

    @Test
    void aTenantWithNoSubscriptionRowIsLazilyStartedOnFree() {
        SubscriptionService service = new SubscriptionService(adapt(new FakeRepo()));
        UUID tenantId = UUID.randomUUID();

        Subscription current = service.current(tenantId);

        assertThat(current.getPlan()).isEqualTo("FREE");
        assertThat(current.getCommissionBps()).isEqualTo(Plan.FREE.commissionBps());
    }

    @Test
    void currentIsIdempotentOnceAPlanExists() {
        SubscriptionService service = new SubscriptionService(adapt(new FakeRepo()));
        UUID tenantId = UUID.randomUUID();

        Subscription first = service.current(tenantId);
        Subscription second = service.current(tenantId);

        assertThat(second.getId()).isEqualTo(first.getId());
    }

    @Test
    void changingPlanEndsTheOldRowAndStartsANewOneRatherThanMutatingInPlace() {
        SubscriptionService service = new SubscriptionService(adapt(new FakeRepo()));
        UUID tenantId = UUID.randomUUID();

        Subscription free = service.current(tenantId);
        assertThat(free.getEndedAt()).isNull();

        Subscription growth = service.changePlan(tenantId, Plan.GROWTH);

        assertThat(free.getEndedAt()).isNotNull();
        assertThat(growth.getPlan()).isEqualTo("GROWTH");
        assertThat(growth.getId()).isNotEqualTo(free.getId());
        assertThat(service.current(tenantId).getId()).isEqualTo(growth.getId());
    }
}
