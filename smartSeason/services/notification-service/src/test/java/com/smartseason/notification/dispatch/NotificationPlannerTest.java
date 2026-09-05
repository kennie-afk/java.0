package com.smartseason.notification.dispatch;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class NotificationPlannerTest {

    private static final Instant MIDDAY = Instant.parse("2026-03-02T09:00:00Z");
    private static final Instant NIGHT = Instant.parse("2026-03-02T20:30:00Z");

    private final NotificationPlanner planner =
            new NotificationPlanner(new TemplateRenderer(), QuietHours.eastAfricaNight());

    private TemplateSet bilingual() {
        return new TemplateSet()
                .put("en", "Payment received", "Hello {{name}}, we received {{amount}} KES.")
                .put("sw", "Malipo yamepokelewa", "Habari {{name}}, tumepokea KES {{amount}}.");
    }

    private Map<String, Object> vars() {
        return Map.of("name", "Amina", "amount", "4,500");
    }

    @Test
    @DisplayName("an English request renders the English template")
    void rendersEnglish() {
        DispatchDecision decision = planner.plan(bilingual(), "en",
                DeliveryChannel.SMS, vars(), false, MIDDAY);

        assertThat(decision.shouldSend()).isTrue();
        assertThat(decision.locale()).isEqualTo("en");
        assertThat(decision.renderedBody()).isEqualTo("Hello Amina, we received 4,500 KES.");
        assertThat(decision.renderedSubject()).isEqualTo("Payment received");
    }

    @Test
    @DisplayName("a Swahili request renders the Swahili template")
    void rendersSwahili() {
        DispatchDecision decision = planner.plan(bilingual(), "sw",
                DeliveryChannel.SMS, vars(), false, MIDDAY);

        assertThat(decision.locale()).isEqualTo("sw");
        assertThat(decision.renderedBody()).isEqualTo("Habari Amina, tumepokea KES 4,500.");
    }

    @Test
    @DisplayName("a locale with a region tag still resolves, so sw-KE gets Swahili")
    void resolvesRegionTaggedLocale() {
        assertThat(planner.plan(bilingual(), "sw-KE", DeliveryChannel.SMS, vars(), false, MIDDAY)
                .locale()).isEqualTo("sw");
    }

    @Test
    @DisplayName("an unsupported locale falls back to English rather than failing")
    void unsupportedLocaleFallsBack() {
        assertThat(planner.plan(bilingual(), "fr", DeliveryChannel.SMS, vars(), false, MIDDAY)
                .locale()).isEqualTo("en");
    }

    @Test
    @DisplayName("a locale with no template falls back to English")
    void missingLocaleFallsBackToEnglish() {
        TemplateSet englishOnly = new TemplateSet()
                .put("en", "Subject", "Body for {{name}}");

        DispatchDecision decision = planner.plan(englishOnly, "sw",
                DeliveryChannel.SMS, vars(), false, MIDDAY);

        assertThat(decision.shouldSend()).isTrue();
        assertThat(decision.locale()).isEqualTo("en");
    }

    @Nested
    class Suppression {

        @Test
        @DisplayName("a message with an unfilled variable is suppressed, not sent half-blank")
        void missingVariableSuppresses() {
            DispatchDecision decision = planner.plan(bilingual(), "en",
                    DeliveryChannel.SMS, Map.of("name", "Amina"), false, MIDDAY);

            assertThat(decision.outcome()).isEqualTo(DispatchDecision.Outcome.SUPPRESS);
            assertThat(decision.reason()).contains("amount");
        }

        @Test
        @DisplayName("a code with no template at all is suppressed with a clear reason")
        void noTemplateSuppresses() {
            DispatchDecision decision = planner.plan(new TemplateSet(), "en",
                    DeliveryChannel.SMS, vars(), false, MIDDAY);

            assertThat(decision.outcome()).isEqualTo(DispatchDecision.Outcome.SUPPRESS);
            assertThat(decision.reason()).contains("no template");
        }
    }

    @Nested
    class Quiet {

        @Test
        @DisplayName("a routine SMS at night is deferred to the morning, not sent")
        void nightSmsIsDeferred() {
            DispatchDecision decision = planner.plan(bilingual(), "en",
                    DeliveryChannel.SMS, vars(), false, NIGHT);

            assertThat(decision.outcome()).isEqualTo(DispatchDecision.Outcome.DEFER);
            assertThat(decision.deferredUntil()).isAfter(NIGHT);
        }

        @Test
        @DisplayName("a critical message ignores quiet hours")
        void criticalIgnoresQuietHours() {
            assertThat(planner.plan(bilingual(), "en", DeliveryChannel.SMS, vars(), true, NIGHT)
                    .shouldSend()).isTrue();
        }

        @Test
        @DisplayName("USSD and email are not subject to quiet hours because they are pulled, not pushed")
        void pulledChannelsIgnoreQuietHours() {
            assertThat(planner.plan(bilingual(), "en", DeliveryChannel.USSD, vars(), false, NIGHT)
                    .shouldSend()).isTrue();
            assertThat(planner.plan(bilingual(), "en", DeliveryChannel.EMAIL, vars(), false, NIGHT)
                    .shouldSend()).isTrue();
        }

        @Test
        @DisplayName("quiet hours that wrap past midnight are handled correctly")
        void wrappingWindowHandled() {
            QuietHours night = QuietHours.eastAfricaNight();

            assertThat(night.covers(Instant.parse("2026-03-02T21:00:00Z"))).isTrue();
            assertThat(night.covers(Instant.parse("2026-03-02T01:00:00Z"))).isTrue();
            assertThat(night.covers(Instant.parse("2026-03-02T09:00:00Z"))).isFalse();
        }
    }

    @Test
    @DisplayName("the mock SMS adapter refuses an oversized message rather than truncating it")
    void adapterRejectsOversizedMessage() {
        LoggingSmsAdapter adapter = new LoggingSmsAdapter();

        DeliveryResult result = adapter.send("254712345678", null, "x".repeat(1601));

        assertThat(result.accepted()).isFalse();
        assertThat(result.detail()).contains("1600");
    }

    @Test
    @DisplayName("the mock SMS adapter accepts a normal message and returns a reference")
    void adapterAcceptsMessage() {
        LoggingSmsAdapter adapter = new LoggingSmsAdapter();

        DeliveryResult result = adapter.send("254712345678", null, "Hello");

        assertThat(result.accepted()).isTrue();
        assertThat(result.providerRef()).startsWith("mock-");
        assertThat(adapter.sent()).hasSize(1);
    }
}
