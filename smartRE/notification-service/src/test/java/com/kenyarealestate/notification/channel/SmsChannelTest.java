package com.kenyarealestate.notification.channel;

import com.kenyarealestate.notification.entity.Channel;
import com.kenyarealestate.notification.entity.Notification;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class SmsChannelTest {

    private final RestTemplate rest = mock(RestTemplate.class);

    private SmsChannel configured() {
        return new SmsChannel(rest, "sandbox", "key-123", "SMARTRE", "https://sms.example/send");
    }

    private SmsChannel unconfigured() {
        return new SmsChannel(rest, "", "", "", "https://sms.example/send");
    }

    private Notification sms(String phone) {
        return Notification.builder()
                .id(UUID.randomUUID())
                .channel(Channel.SMS)
                .templateCode("RENT_DUE")
                .recipientPhone(phone)
                .body("SmartRE: Rent for B1 of KES 120,000 is due Sep 10.")
                .build();
    }

    @Nested
    @DisplayName("Kenyan numbers arrive in three shapes and the provider accepts one")
    class Normalisation {

        @Test
        @DisplayName("a local 07… number becomes E.164")
        void localFormat() {
            assertThat(SmsChannel.normalise("0712345678")).isEqualTo("+254712345678");
        }

        @Test
        @DisplayName("a 254… number gains its plus")
        void countryCodeWithoutPlus() {
            assertThat(SmsChannel.normalise("254712345678")).isEqualTo("+254712345678");
        }

        @Test
        @DisplayName("an already-correct number is left alone")
        void alreadyE164() {
            assertThat(SmsChannel.normalise("+254712345678")).isEqualTo("+254712345678");
        }

        @Test
        @DisplayName("spaces and dashes as typed by a person are stripped")
        void humanFormatting() {
            assertThat(SmsChannel.normalise("0712 345 678")).isEqualTo("+254712345678");
            assertThat(SmsChannel.normalise("+254-712-345-678")).isEqualTo("+254712345678");
        }

        @Test
        @DisplayName("nothing usable stays nothing, rather than becoming a plausible wrong number")
        void unusable() {
            assertThat(SmsChannel.normalise(null)).isNull();
            assertThat(SmsChannel.normalise("   ")).isNull();
            assertThat(SmsChannel.normalise("12345")).isNull();
        }
    }

    @Nested
    @DisplayName("delivery")
    class Delivery {

        @Test
        @DisplayName("with no credentials it logs and reports success, so the preference toggle is not a lie")
        void unconfiguredDoesNotFail() throws Exception {
            unconfigured().deliver(sms("0712345678"));
            verifyNoInteractions(rest);
        }

        @Test
        @DisplayName("a configured channel posts the normalised number and the sender id")
        void postsToTheProvider() throws Exception {
            when(rest.postForEntity(any(String.class), any(), eq(String.class)))
                    .thenReturn(new ResponseEntity<>("{}", HttpStatus.CREATED));

            configured().deliver(sms("0712345678"));

            @SuppressWarnings("unchecked")
            var captor = org.mockito.ArgumentCaptor.forClass(HttpEntity.class);
            verify(rest).postForEntity(eq("https://sms.example/send"), captor.capture(), eq(String.class));

            @SuppressWarnings("unchecked")
            MultiValueMap<String, String> form = (MultiValueMap<String, String>) captor.getValue().getBody();
            assertThat(form).isNotNull();
            assertThat(form.getFirst("to")).isEqualTo("+254712345678");
            assertThat(form.getFirst("from")).isEqualTo("SMARTRE");
            assertThat(captor.getValue().getHeaders().getFirst("apiKey")).isEqualTo("key-123");
        }

        @Test
        @DisplayName("no phone number is a failure worth recording, not a silent skip")
        void noRecipient() {
            assertThatThrownBy(() -> configured().deliver(sms(null)))
                    .isInstanceOf(DeliveryException.class);
        }

        @Test
        @DisplayName("a provider error is thrown so the retry job picks it up")
        void providerErrorRetries() {
            when(rest.postForEntity(any(String.class), any(), eq(String.class)))
                    .thenThrow(new RuntimeException("gateway timeout"));

            assertThatThrownBy(() -> configured().deliver(sms("0712345678")))
                    .isInstanceOf(DeliveryException.class)
                    .hasMessageContaining("gateway timeout");
        }
    }
}
