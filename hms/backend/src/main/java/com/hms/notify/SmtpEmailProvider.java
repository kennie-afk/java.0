package com.hms.notify;

import jakarta.mail.Authenticator;
import jakarta.mail.Message;
import jakarta.mail.MessagingException;
import jakarta.mail.PasswordAuthentication;
import jakarta.mail.SendFailedException;
import jakarta.mail.Session;
import jakarta.mail.Transport;
import jakarta.mail.internet.AddressException;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import java.util.Properties;

/**
 * E-mail over SMTP with Jakarta Mail. Tested against a small fake SMTP server (plain, with AUTH PLAIN); NEVER run against a real mail
 * provider, and TLS (STARTTLS or implicit) has not been exercised. Messages are plain text, UTF-8.
 */
final class SmtpEmailProvider implements NotificationProvider {

    record Config(String host, int port, String username, String password, String from, boolean startTls, boolean ssl) {}

    private final Config config;
    private final Session session;

    SmtpEmailProvider(Config config) {
        this.config = config;
        Properties p = new Properties();
        p.put("mail.smtp.host", config.host());
        p.put("mail.smtp.port", Integer.toString(config.port()));
        p.put("mail.smtp.connectiontimeout", "5000");
        p.put("mail.smtp.timeout", "20000");
        p.put("mail.smtp.writetimeout", "20000");
        p.put("mail.smtp.starttls.enable", Boolean.toString(config.startTls()));
        p.put("mail.smtp.starttls.required", Boolean.toString(config.startTls()));
        p.put("mail.smtp.ssl.enable", Boolean.toString(config.ssl()));
        boolean auth = config.username() != null && !config.username().isBlank();
        p.put("mail.smtp.auth", Boolean.toString(auth));
        this.session = auth ? Session.getInstance(p, new Authenticator() {
            @Override
            protected PasswordAuthentication getPasswordAuthentication() {
                return new PasswordAuthentication(config.username(), config.password());
            }
        }) : Session.getInstance(p);
    }

    @Override
    public String channel() {
        return "EMAIL";
    }

    @Override
    public boolean isMock() {
        return false;
    }

    @Override
    public void send(Outbound m) throws DeliveryException {
        try {
            MimeMessage msg = new MimeMessage(session);
            msg.setFrom(new InternetAddress(config.from(), true));
            msg.setRecipient(Message.RecipientType.TO, new InternetAddress(m.recipient(), true));
            msg.setSubject(m.subject(), "UTF-8");
            msg.setText(m.body(), "UTF-8");
            Transport.send(msg);
        } catch (AddressException e) {
            throw new DeliveryException("The e-mail address is not valid.", true);
        } catch (jakarta.mail.AuthenticationFailedException e) {
            throw new DeliveryException("The mail server refused the configured username and password.", true);
        } catch (SendFailedException e) {
            // Invalid or rejected recipient: the server said no to this address.
            throw new DeliveryException("The mail server rejected the message or recipient.", e.getInvalidAddresses() != null && e.getInvalidAddresses().length > 0);
        } catch (MessagingException e) {
            throw new DeliveryException("The mail server could not be reached or failed (" + e.getClass().getSimpleName() + ").", false);
        }
    }
}
