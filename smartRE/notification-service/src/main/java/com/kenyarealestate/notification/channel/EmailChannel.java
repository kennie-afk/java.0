package com.kenyarealestate.notification.channel;

import com.kenyarealestate.notification.entity.Channel;
import com.kenyarealestate.notification.entity.Notification;
import com.kenyarealestate.notification.service.EmailHtmlShell;
import jakarta.mail.internet.MimeMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;

@Slf4j
@Component
public class EmailChannel implements DeliveryChannel {

    private final JavaMailSender mailSender;
    private final boolean configured;
    private final String fromAddress;
    private final Resource logo;

    public EmailChannel(ObjectProvider<JavaMailSender> mailSenderProvider,
                        @Value("${spring.mail.host:}") String mailHost,
                        @Value("${mail.from:no-reply@smartre.co.ke}") String fromAddress) {
        this.configured = StringUtils.hasText(mailHost);
        this.mailSender = configured ? mailSenderProvider.getIfAvailable() : null;
        this.fromAddress = fromAddress;
        // Attached rather than linked, so it renders even with remote images blocked.
        Resource candidate = new ClassPathResource("static/logo-email.png");
        this.logo = candidate.exists() ? candidate : null;
        if (this.logo == null) {
            log.warn("static/logo-email.png is missing — branded mail will send without its mark.");
        }
        if (!configured) {
            log.warn("spring.mail.host is not set — email notifications will be logged instead of sent. "
                    + "Set MAIL_HOST/MAIL_USERNAME/MAIL_PASSWORD to enable real delivery.");
        }
    }

    @Override
    public Channel type() { return Channel.EMAIL; }

    @Override
    public void deliver(Notification n) throws DeliveryException {
        if (!StringUtils.hasText(n.getRecipientEmail())) {
            throw new DeliveryException("No recipient email resolved for notification " + n.getId());
        }

        if (!configured || mailSender == null) {
            log.info("EMAIL (not sent, no mail server configured) to={} subject={}\n{}",
                    n.getRecipientEmail(), n.getSubject(), n.getBody());
            return;
        }

        try {
            if (StringUtils.hasText(n.getHtmlBody())) {
                sendMultipart(n);
            } else {
                // No HTML alternative — an SMS-shaped or legacy template. Still send it
                // rather than failing: a plain message delivered beats a pretty one that
                // never arrives.
                SimpleMailMessage message = new SimpleMailMessage();
                message.setFrom(fromAddress);
                message.setTo(n.getRecipientEmail());
                message.setSubject(n.getSubject());
                message.setText(n.getBody());
                mailSender.send(message);
            }
        } catch (Exception e) {
            throw new DeliveryException("SMTP send failed: " + e.getMessage(), e);
        }
    }

    /**
     * multipart/related wrapping multipart/alternative: the text and HTML versions are
     * alternatives to each other, and the logo is a related part that only the HTML one
     * refers to. Getting that nesting wrong is why branded mail sometimes arrives with
     * the logo as a visible attachment paperclip.
     */
    private void sendMultipart(Notification n) throws Exception {
        MimeMessage mime = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(
                mime, MimeMessageHelper.MULTIPART_MODE_MIXED_RELATED, StandardCharsets.UTF_8.name());

        helper.setFrom(fromAddress);
        helper.setTo(n.getRecipientEmail());
        helper.setSubject(n.getSubject() == null ? "SmartRE" : n.getSubject());
        // Text first, HTML second — the order is the contract: a client picks the last
        // alternative it can render.
        helper.setText(n.getBody(), n.getHtmlBody());

        if (logo != null && logo.exists()) {
            helper.addInline(EmailHtmlShell.LOGO_CID, logo, "image/png");
        }

        mailSender.send(mime);
    }
}
