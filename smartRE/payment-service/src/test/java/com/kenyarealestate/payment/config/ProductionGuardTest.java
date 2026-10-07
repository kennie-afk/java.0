package com.kenyarealestate.payment.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;

class ProductionGuardTest {

    private static final String LIVE_AUTH = "https://api.safaricom.co.ke/oauth/v1/generate?grant_type=client_credentials";
    private static final String LIVE_STK = "https://api.safaricom.co.ke/mpesa/stkpush/v1/processrequest";
    private static final String CALLBACK = "https://smartre.co.ke/api/payments/mpesa/callback";

    private static List<String> live(String mode, String key, String secret, String passkey, String shortcode,
                                     String callback, String auth, String stk) {
        return ProductionGuard.violations("production", mode, key, secret, passkey, shortcode, callback, auth, stk);
    }

    @Test
    void outsideProductionAnythingGoes() {
        assertThat(ProductionGuard.violations("development", "mock", "placeholder", "", "placeholder", "174379",
                "https://your-domain.com/x", "https://sandbox.safaricom.co.ke/a", "https://sandbox.safaricom.co.ke/b")).isEmpty();
        assertThat(ProductionGuard.violations(null, "mock", "", "", "", "", "", "", "")).isEmpty();
    }

    @Test
    void productionRefusesMockMode() {
        assertThat(live("mock", "k", "s", "p", "600123", CALLBACK, LIVE_AUTH, LIVE_STK))
                .singleElement().asString().contains("mock");
    }

    @Test
    void productionRefusesPlaceholderCredentials() {
        List<String> problems = live("daraja", "placeholder", "", "placeholder", "600123", CALLBACK, LIVE_AUTH, LIVE_STK);

        assertThat(problems).hasSize(3);
        assertThat(problems).anyMatch(p -> p.contains("MPESA_CONSUMER_KEY"));
        assertThat(problems).anyMatch(p -> p.contains("MPESA_CONSUMER_SECRET"));
        assertThat(problems).anyMatch(p -> p.contains("MPESA_PASSKEY"));
    }

    @Test
    void productionRefusesTheSandboxShortcodeCallbackAndUrls() {
        List<String> problems = live("daraja", "k", "s", "p", "174379",
                "https://your-domain.com/api/payments/mpesa/callback",
                "https://sandbox.safaricom.co.ke/oauth/v1/generate", "https://sandbox.safaricom.co.ke/mpesa/stkpush/v1/processrequest");

        assertThat(problems).hasSize(3);
        assertThat(problems).anyMatch(p -> p.contains("MPESA_SHORTCODE"));
        assertThat(problems).anyMatch(p -> p.contains("MPESA_CALLBACK_URL"));
        assertThat(problems).anyMatch(p -> p.contains("sandbox"));
    }

    @Test
    void aFullyLiveConfigurationPasses() {
        assertThat(live("daraja", "k", "s", "p", "600123", CALLBACK, LIVE_AUTH, LIVE_STK)).isEmpty();
    }

    @Test
    void theGuardBeanThrowsSoTheProcessDoesNotStart() {
        assertThatThrownBy(() -> new ProductionGuard("production", "mock", "placeholder", "", "placeholder", "174379",
                "", "", ""))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Refusing to start payment-service in production");
        new ProductionGuard("development", "mock", "placeholder", "", "placeholder", "174379", "", "", "");
    }
}
