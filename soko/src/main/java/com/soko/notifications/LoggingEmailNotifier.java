package com.soko.notifications;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * The only {@link EmailNotifier} wired up right now. It sends nothing --
 * there is no mail provider configured, on purpose, because that needs a
 * real account and a real sending domain that only the business owner can
 * supply. What it does instead is log exactly what would have been sent and
 * to whom, at INFO level, so every notification event this system fires is
 * visible and provable before a single real email goes out.
 *
 * <p>Swapping this for a real sender later is a one-class change: implement
 * {@link EmailNotifier} against whichever provider is chosen (SMTP via
 * spring-boot-starter-mail, or an HTTP API like SendGrid/Mailgun/SES) and
 * mark it {@code @Primary}, or remove this bean. Nothing that calls
 * {@link EmailNotifier} needs to change.
 */
@Component
public class LoggingEmailNotifier implements EmailNotifier {

    private static final Logger log = LoggerFactory.getLogger(LoggingEmailNotifier.class);

    @Override
    public void send(String to, String subject, String body) {
        log.info(
                "EMAIL not sent (no mail provider configured) -- to: {}, subject: {}\n{}",
                to,
                subject,
                body);
    }
}
