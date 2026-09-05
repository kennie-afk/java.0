package com.smartseason.notification.dispatch;

import java.time.Instant;
import java.util.Map;

public class NotificationPlanner {

    private final TemplateRenderer renderer;
    private final QuietHours quietHours;

    public NotificationPlanner(TemplateRenderer renderer, QuietHours quietHours) {
        this.renderer = renderer;
        this.quietHours = quietHours;
    }

    public DispatchDecision plan(TemplateSet templates,
                                 String requestedLocale,
                                 DeliveryChannel channel,
                                 Map<String, Object> variables,
                                 boolean critical,
                                 Instant at) {
        String locale = null;
        String subject = null;
        String body = null;

        for (String candidate : LocaleResolver.fallbackChain(requestedLocale)) {
            TemplateSet.Entry entry = templates.forLocale(candidate);
            if (entry != null) {
                locale = candidate;
                subject = entry.subject();
                body = entry.body();
                break;
            }
        }

        if (body == null) {
            return new DispatchDecision(DispatchDecision.Outcome.SUPPRESS, null, null,
                    LocaleResolver.resolve(requestedLocale), null,
                    "no template exists for this code in any supported locale");
        }

        TemplateRenderer.Rendered renderedBody = renderer.render(body, variables);
        TemplateRenderer.Rendered renderedSubject = renderer.render(subject, variables);

        if (!renderedBody.complete()) {
            return new DispatchDecision(DispatchDecision.Outcome.SUPPRESS,
                    renderedSubject.text(), renderedBody.text(), locale, null,
                    "template variables missing: " + String.join(", ", renderedBody.missingVariables()));
        }

        if (!critical && channel.respectsQuietHours() && quietHours.covers(at)) {
            return new DispatchDecision(DispatchDecision.Outcome.DEFER,
                    renderedSubject.text(), renderedBody.text(), locale,
                    quietHours.nextOpening(at), "deferred until quiet hours end");
        }

        return new DispatchDecision(DispatchDecision.Outcome.SEND,
                renderedSubject.text(), renderedBody.text(), locale, null, null);
    }
}
