package com.kenyarealestate.notification.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;

class ProductionGuardTest {

    private static List<String> v(String env, boolean allow, String smsUser, String smsKey, String host, String user, String pass) {
        return ProductionGuard.violations(env, allow, smsUser, smsKey, host, user, pass);
    }

    @Test
    void developmentAndUnsetEnvironmentsKeepTheLogInsteadOfSendBehaviour() {
        assertThat(v("development", false, "", "", "", "", "")).isEmpty();
        assertThat(v(null, false, "placeholder", "placeholder", "", "", "")).isEmpty();
        assertThat(v("staging", false, "", "", "", "", "")).isEmpty();
    }

    @Test
    void productionRefusesToStartWithSmsAndMailUnset() {
        List<String> problems = v("production", false, "", "", "", "", "");

        assertThat(problems).hasSize(2);
        assertThat(problems).anyMatch(p -> p.contains("SMS_API_KEY"));
        assertThat(problems).anyMatch(p -> p.contains("MAIL_HOST"));
    }

    @Test
    void theEnvironmentNameIsMatchedWithoutRegardToCaseOrSpaces() {
        assertThat(v(" Production ", false, "", "", "", "", "")).isNotEmpty();
    }

    @Test
    void aPlaceholderKeyIsRefusedEvenWhenUnconfiguredProvidersAreAllowed() {
        // The k8s example secret ships "placeholder"; the channel would call the provider with it.
        List<String> problems = v("production", true, "placeholder", "placeholder", "smtp.example.org", "mailer", "s3cret");

        assertThat(problems).singleElement().asString().contains("SMS_USERNAME/SMS_API_KEY");
    }

    @Test
    void aHalfConfiguredSmsPairIsRefused() {
        assertThat(v("production", true, "smartre", "", "smtp.example.org", "mailer", "s3cret")).hasSize(1);
        assertThat(v("production", true, "", "key-123", "smtp.example.org", "mailer", "s3cret")).hasSize(1);
    }

    @Test
    void aMailHostWithPlaceholderCredentialsIsRefused() {
        assertThat(v("production", true, "smartre", "key-123", "smtp.gmail.com", "placeholder", "placeholder"))
                .singleElement().asString().contains("MAIL_USERNAME");
    }

    @Test
    void realCredentialsPass() {
        assertThat(v("production", false, "smartre", "atsk_live_123", "smtp.example.org", "mailer", "s3cret")).isEmpty();
    }

    @Test
    void leavingProvidersOffIsAcceptableOnlyWhenItIsAcknowledged() {
        assertThat(v("production", false, "", "", "", "", "")).isNotEmpty();
        assertThat(v("production", true, "", "", "", "", "")).isEmpty();
    }

    @Test
    void theGuardBeanThrowsSoTheProcessDoesNotStart() {
        assertThatThrownBy(() -> new ProductionGuard("production", false, "", "", "", "", ""))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Refusing to start notification-service in production");
        new ProductionGuard("development", false, "", "", "", "", "");
    }
}
