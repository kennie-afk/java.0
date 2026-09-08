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
    private final EmailHtmlShell shell;

    public TemplateRenderer(NotificationTemplateRepository templates,
                            @Qualifier("notificationTemplateEngine") TemplateEngine engine,
                            EmailHtmlShell shell) {
        this.templates = templates;
        this.engine = engine;
        this.shell = shell;
    }

    /**
     * @param html the text/html alternative, or null when the template has none — SMS and
     *             in-app templates never do, and an email without one is sent as plain
     *             text rather than not sent at all.
     */
    public record Rendered(String subject, String body, String html) {}

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

        // The fragment is rendered through the same engine as the text body, so the two
        // alternatives always describe the same data, then wrapped in the shell.
        String html = null;
        if (template.getHtmlBodyTemplate() != null && !template.getHtmlBodyTemplate().isBlank()) {
            html = shell.wrap(engine.process(template.getHtmlBodyTemplate(), ctx));
        }
        return new Rendered(subject, body, html);
    }
}
