package com.kenyarealestate.notification.service;

import com.kenyarealestate.notification.entity.Channel;
import com.kenyarealestate.notification.entity.NotificationTemplate;
import com.kenyarealestate.notification.exception.NotFoundException;
import com.kenyarealestate.notification.repository.NotificationTemplateRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.util.Map;

@Slf4j
@Service
public class TemplateRenderer {

    private static final String DEFAULT_LOCALE = "en";

    private final NotificationTemplateRepository templates;
    private final TemplateEngine engine;

    public TemplateRenderer(NotificationTemplateRepository templates,
                            @Qualifier("notificationTemplateEngine") TemplateEngine engine) {
        this.templates = templates;
        this.engine = engine;
    }

    public record Rendered(String subject, String body) {}

    public NotificationTemplate findTemplate(String code, Channel channel) {
        return templates.findByCodeAndChannelAndLocaleAndActiveTrue(code, channel, DEFAULT_LOCALE)
                .orElseThrow(() -> new NotFoundException(
                        "No active " + channel + " template for code '" + code + "'"));
    }

    public Rendered render(NotificationTemplate template, Map<String, Object> model) {
        Context ctx = new Context();
        model.forEach(ctx::setVariable);

        String subject = template.getSubjectTemplate() == null ? null
                : engine.process(template.getSubjectTemplate(), ctx).trim();
        String body = engine.process(template.getBodyTemplate(), ctx);
        return new Rendered(subject, body);
    }
}
