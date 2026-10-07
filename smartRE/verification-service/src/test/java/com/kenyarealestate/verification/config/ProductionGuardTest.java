package com.kenyarealestate.verification.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;

class ProductionGuardTest {

    private static List<String> v(String env, boolean allow, boolean smile, String partner, String smileKey,
                                  boolean ardhisasa, String ardhisasaKey, boolean analysis, String gemini) {
        return ProductionGuard.violations(env, allow, smile, partner, smileKey, ardhisasa, ardhisasaKey, analysis, gemini);
    }

    @Test
    void outsideProductionThePlaceholderDefaultsAreFine() {
        assertThat(v("development", false, false, "placeholder", "placeholder", false, "placeholder", false, "")).isEmpty();
        assertThat(v(null, false, true, "placeholder", "placeholder", true, "placeholder", true, "")).isEmpty();
    }

    @Test
    void productionRefusesProvidersThatAreSwitchedOff() {
        List<String> problems = v("production", false, false, "", "", false, "", false, "");

        assertThat(problems).anyMatch(p -> p.contains("Smile Identity is off"));
        assertThat(problems).anyMatch(p -> p.contains("Ardhisasa is off"));
        assertThat(problems).anyMatch(p -> p.contains("document analysis is off"));
        assertThat(problems).anyMatch(p -> p.contains("SMARTRE_ALLOW_UNCONFIGURED_PROVIDERS"));
    }

    @Test
    void anAcknowledgedManualOnlyDeploymentStarts() {
        assertThat(v("production", true, false, "", "", false, "", false, "")).isEmpty();
    }

    @Test
    void anEnabledProviderWithAPlaceholderKeyIsNeverAcceptable() {
        List<String> problems = v("production", true, true, "placeholder", "placeholder", true, "placeholder", true, "");

        assertThat(problems).hasSize(3);
        assertThat(problems).anyMatch(p -> p.contains("SMILE_IDENTITY_PARTNER_ID"));
        assertThat(problems).anyMatch(p -> p.contains("ARDHISASA_API_KEY"));
        assertThat(problems).anyMatch(p -> p.contains("GEMINI_API_KEY"));
    }

    @Test
    void oneMissingSmileCredentialIsEnough() {
        assertThat(v("production", true, true, "partner-1", "", false, "", false, "")).hasSize(1);
        assertThat(v("production", true, true, "", "key", false, "", false, "")).hasSize(1);
    }

    @Test
    void everyProviderOnWithRealKeysPasses() {
        assertThat(v("production", false, true, "p-123", "k-456", true, "a-789", true, "g-000")).isEmpty();
    }

    @Test
    void theGuardBeanThrowsSoTheProcessDoesNotStart() {
        assertThatThrownBy(() -> new ProductionGuard("production", false, true, "placeholder", "placeholder",
                false, "", false, ""))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Refusing to start verification-service in production");
        new ProductionGuard("development", false, false, "placeholder", "placeholder", false, "placeholder", false, "");
    }
}
