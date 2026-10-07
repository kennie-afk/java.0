package com.soko.platform;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class ProductionGuardTest {

    private static final String LONG_SECRET = "0123456789abcdef0123456789abcdef";

    @Test
    void developmentAcceptsAnythingSoTheDemoStillRuns() {
        assertThat(ProductionGuard.violations("development", "mock", "short", 3)).isEmpty();
        assertThat(ProductionGuard.violations(null, "mock", "short", 3)).isEmpty();
    }

    @Test
    void productionRefusesMockMpesa() {
        assertThat(ProductionGuard.violations("production", "mock", LONG_SECRET, 0))
                .singleElement().asString().contains("live");
    }

    @Test
    void productionRefusesAShortJwtSecretAndTheAutoAnsweringCustomer() {
        List<String> problems = ProductionGuard.violations("PRODUCTION", "live", "short", 5);
        assertThat(problems).hasSize(2);
    }

    @Test
    void aCorrectProductionConfigurationPasses() {
        assertThat(ProductionGuard.violations("production", "live", LONG_SECRET, 0)).isEmpty();
    }

    @Test
    void productionWithoutMailIsRefusedBecauseResetLinksWouldOnlyBeLogged() {
        assertThat(ProductionGuard.violations("production", "live", LONG_SECRET, 0, false))
                .singleElement().asString().contains("SOKO_MAIL_ENABLED");
        assertThat(ProductionGuard.violations("production", "live", LONG_SECRET, 0, true)).isEmpty();
        assertThat(ProductionGuard.violations("development", "mock", "short", 3, false)).isEmpty();
    }
}
