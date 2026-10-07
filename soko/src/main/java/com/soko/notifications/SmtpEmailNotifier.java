package com.soko.notifications;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

/**
 * Sends real e-mail over SMTP. Active only when {@code SOKO_MAIL_ENABLED=true}; the connection
 * itself is Spring's standard mail configuration, so {@code SPRING_MAIL_HOST}, {@code
 * SPRING_MAIL_PORT}, {@code SPRING_MAIL_USERNAME}, {@code SPRING_MAIL_PASSWORD} and
 * {@code SPRING_MAIL_PROPERTIES_MAIL_SMTP_STARTTLS_ENABLE=true} apply. Without it the
 * {@link LoggingEmailNotifier} stays in place and nothing leaves the process.
 *
 * <p>A send failure throws, so the caller decides what it means (a password reset logs it and
 * still answers the same way, so the response never reveals whether the account exists).
 */
@Component
@ConditionalOnProperty(name = "soko.mail.enabled", havingValue = "true")
public class SmtpEmailNotifier implements EmailNotifier {

    private final JavaMailSender mailer;
    private final String from;

    public SmtpEmailNotifier(JavaMailSender mailer, @Value("${soko.mail.from}") String from) {
        this.mailer = mailer;
        this.from = from;
    }

    @Override
    public void send(String to, String subject, String body) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(to);
        message.setSubject(subject);
        message.setText(body);
        mailer.send(message);
    }
}
