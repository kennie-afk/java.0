package com.soko.notifications;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

class SmtpEmailNotifierTest {

    @Test
    void handsTheMessageToTheMailSenderFromTheConfiguredAddress() {
        JavaMailSender mailer = mock(JavaMailSender.class);
        new SmtpEmailNotifier(mailer, "no-reply@freshferm.example")
                .send("amina@example.org", "Subject", "Body text");

        ArgumentCaptor<SimpleMailMessage> sent = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailer).send(sent.capture());
        assertThat(sent.getValue().getFrom()).isEqualTo("no-reply@freshferm.example");
        assertThat(sent.getValue().getTo()).containsExactly("amina@example.org");
        assertThat(sent.getValue().getSubject()).isEqualTo("Subject");
        assertThat(sent.getValue().getText()).isEqualTo("Body text");
    }
}
