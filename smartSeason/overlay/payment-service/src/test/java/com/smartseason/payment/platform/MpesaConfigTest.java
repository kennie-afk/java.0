package com.smartseason.payment.platform;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class MpesaConfigTest {

    @Test
    void developmentAcceptsMockSoTheDemoStillRuns() {
        assertThat(MpesaConfig.productionProblem("development", "mock")).isNull();
        assertThat(MpesaConfig.productionProblem(null, "mock")).isNull();
    }

    @Test
    void productionRefusesMockMpesa() {
        assertThat(MpesaConfig.productionProblem("production", "mock")).contains("live");
        assertThat(MpesaConfig.productionProblem("PRODUCTION", "mock")).isNotNull();
    }

    @Test
    void productionAcceptsLive() {
        assertThat(MpesaConfig.productionProblem("production", "live")).isNull();
    }
}
