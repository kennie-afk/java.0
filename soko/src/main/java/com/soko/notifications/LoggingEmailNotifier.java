package com.soko.notifications;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * The only {@link EmailNotifier} wired up right now. It sends nothing --
 * there is no mail provider configured, on purpose, because that needs a
 * real account and a real sending domain that only the business owner can
 * supply. What it does instead is log exactly what would have been sent and
 * to whom, at INFO level, so every notification event this system fires is
 * visible and provable before a single real email goes out.
 *
 * <p>The SMTP implementation is {@link SmtpEmailNotifier}, switched on with
 * {@code SOKO_MAIL_ENABLED=true}; this bean is the default only while that is off.
 */
@Component
@ConditionalOnProperty(name = "soko.mail.enabled", havingValue = "false", matchIfMissing = true)
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
