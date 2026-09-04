package com.kenyarealestate.notification.service;

import com.kenyarealestate.notification.config.TemplateEngineConfig;
import com.kenyarealestate.notification.entity.Category;
import com.kenyarealestate.notification.entity.Channel;
import com.kenyarealestate.notification.entity.NotificationTemplate;
import com.kenyarealestate.notification.exception.NotFoundException;
import com.kenyarealestate.notification.repository.NotificationTemplateRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class TemplateRendererTest {

    private NotificationTemplateRepository templates;
    private TemplateRenderer renderer;

    @BeforeEach
    void setUp() {
        templates = mock(NotificationTemplateRepository.class);
        renderer = new TemplateRenderer(templates, new TemplateEngineConfig().notificationTemplateEngine());
    }

    private NotificationTemplate template(String subject, String body) {
        return NotificationTemplate.builder()
                .code("TEST").channel(Channel.EMAIL).category(Category.PAYMENT)
                .subjectTemplate(subject).bodyTemplate(body).build();
    }

    @Test
    void substitutesVariablesIntoSubjectAndBody() {
        TemplateRenderer.Rendered r = renderer.render(
                template("Payment received — [[${currency}]] [[${amount}]]",
                         "Hello [[${fullName}]],\n\nWe received [[${currency}]] [[${amount}]]."),
                Map.of("fullName", "Wanjiru Kamau", "currency", "KES", "amount", "25000"));

        assertEquals("Payment received — KES 25000", r.subject());
        assertTrue(r.body().contains("Hello Wanjiru Kamau,"));
        assertTrue(r.body().contains("We received KES 25000."));
    }

    @Test
    void textModeLeavesPunctuationAlone() {
        TemplateRenderer.Rendered r = renderer.render(
                template(null, "Your landlord's statement & payout for [[${month}]]."),
                Map.of("month", "September"));

        assertEquals("Your landlord's statement & payout for September.", r.body());
    }

    @Test
    void aTemplateWithNoSubjectRendersBodyOnly() {
        TemplateRenderer.Rendered r = renderer.render(template(null, "Identity verified."), Map.of());
        assertNull(r.subject());
        assertEquals("Identity verified.", r.body());
    }

    @Test
    void missingTemplateIsReportedAsNotFound() {
        when(templates.findByCodeAndChannelAndLocaleAndActiveTrue(any(), any(), any()))
                .thenReturn(Optional.empty());

        NotFoundException e = assertThrows(NotFoundException.class,
                () -> renderer.findTemplate("NO_SUCH_CODE", Channel.SMS));
        assertTrue(e.getMessage().contains("NO_SUCH_CODE"));
        assertTrue(e.getMessage().contains("SMS"));
    }
}
