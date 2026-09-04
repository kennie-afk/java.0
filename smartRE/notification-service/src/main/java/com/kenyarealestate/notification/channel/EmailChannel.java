package com.kenyarealestate.notification.channel;

import com.kenyarealestate.notification.entity.Channel;
import com.kenyarealestate.notification.entity.Notification;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Slf4j
@Component
public class EmailChannel implements DeliveryChannel {

    private final JavaMailSender mailSender;
    private final boolean configured;
    private final String fromAddress;

    public EmailChannel(ObjectProvider<JavaMailSender> mailSenderProvider,
                        @Value("${spring.mail.host:}") String mailHost,
                        @Value("${mail.from:no-reply@smartre.co.ke}") String fromAddress) {
        this.configured = StringUtils.hasText(mailHost);
        this.mailSender = configured ? mailSenderProvider.getIfAvailable() : null;
        this.fromAddress = fromAddress;
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
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(fromAddress);
            message.setTo(n.getRecipientEmail());
            message.setSubject(n.getSubject());
            message.setText(n.getBody());
            mailSender.send(message);
        } catch (Exception e) {
            throw new DeliveryException("SMTP send failed: " + e.getMessage(), e);
        }
    }
}
