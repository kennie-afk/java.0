package com.smartseason.agronomy.casework;

import jakarta.mail.internet.MimeMessage;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.LinkedBlockingQueue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

/**
 * Sends case emails without ever holding up or failing the request that caused them.
 *
 * With no mail server configured nothing is sent and the log says so once per message: the
 * in-app badge is the notification that always works, and email is the nudge on top.
 * Sending runs on a small bounded pool; when the backlog is full a message is dropped and
 * logged rather than growing memory without limit.
 */
@Component
public class CaseMailer {

    private static final Logger log = LoggerFactory.getLogger(CaseMailer.class);

    private final JavaMailSender sender;
    private final String from;
    private final String webUrl;
    private final ExecutorService pool = new ThreadPoolExecutor(1, 4, 30, TimeUnit.SECONDS,
            new LinkedBlockingQueue<>(500), r -> {
                Thread t = new Thread(r, "case-mailer");
                t.setDaemon(true);
                return t;
            }, new ThreadPoolExecutor.AbortPolicy());

    public CaseMailer(ObjectProvider<JavaMailSender> sender,
                      @Value("${smartseason.mail.from:no-reply@smartseason.local}") String from,
                      @Value("${smartseason.web.url:http://localhost:13000}") String webUrl) {
        this.sender = sender.getIfAvailable();
        this.from = from;
        this.webUrl = webUrl.replaceAll("/+$", "");
    }

    public boolean enabled() {
        return sender != null;
    }

    public String caseLink(java.util.UUID caseId) {
        return webUrl + "/cases/" + caseId;
    }

    /** Fire and forget. {@code to} may be null (the person has no email on record). */
    public void send(String to, String subject, String body) {
        if (to == null || to.isBlank()) {
            return;
        }
        if (sender == null) {
            log.info("Mail server not configured; not emailing {} about \"{}\"", mask(to), clean(subject));
            return;
        }
        try {
            pool.execute(() -> deliver(to, clean(subject), body));
        } catch (RuntimeException full) {
            log.warn("Mail backlog full; dropped a message to {}", mask(to));
        }
    }

    private void deliver(String to, String subject, String body) {
        try {
            MimeMessage message = sender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, false, "UTF-8");
            helper.setFrom(from);
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(body, false);
            sender.send(message);
        } catch (Exception ex) {
            log.warn("Could not email {}: {}", mask(to), ex.getMessage());
        }
    }

    /** No line breaks in a subject: that is how mail headers get injected. */
    static String clean(String s) {
        return s == null ? "" : s.replaceAll("[\\r\\n]+", " ").trim();
    }

    private static String mask(String email) {
        int at = email.indexOf('@');
        return at <= 1 ? "***" : email.charAt(0) + "***" + email.substring(at);
    }
}
