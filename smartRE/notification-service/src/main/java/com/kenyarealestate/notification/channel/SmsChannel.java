package com.kenyarealestate.notification.channel;

import com.kenyarealestate.notification.entity.Channel;
import com.kenyarealestate.notification.entity.Notification;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;

/**
 * SMS delivery through Africa's Talking.
 *
 * <p>Chosen because it is the provider that actually reaches Kenyan networks directly and
 * supports alphanumeric sender IDs there; the HTTP contract is small enough that a
 * dedicated SDK would be more dependency than it is worth.
 *
 * <p>The injected RestTemplate is the service's existing one (RestClientConfig): 3s to
 * connect, 5s to read. Tight for an external provider, deliberately — this call sits on
 * the delivery path, and a fast failure hands the message to the retry job rather than
 * holding a worker thread open on a provider that has stopped answering.
 *
 * <p><b>Unconfigured is a supported state, not a failure.</b> With no API key the channel
 * logs what it would have sent and reports success, exactly as EmailChannel does without
 * a mail host. That matters because the notification preferences screen has always
 * offered an SMS toggle per category: before this class existed, switching it on promised
 * a message that nothing could deliver. Now the toggle is honest — the wiring is real and
 * only the credentials are missing.
 */
@Slf4j
@Component
public class SmsChannel implements DeliveryChannel {

    /**
     * Kenyan networks cut a single message at 160 GSM-7 characters and bill per part.
     * Templates are written to fit; this is the backstop so a template edit cannot
     * quietly turn one message into four.
     */
    private static final int SINGLE_SEGMENT = 160;

    private final RestTemplate restTemplate;
    private final String username;
    private final String apiKey;
    private final String senderId;
    private final String endpoint;
    private final boolean configured;

    public SmsChannel(RestTemplate restTemplate,
                      @Value("${sms.username:}") String username,
                      @Value("${sms.api-key:}") String apiKey,
                      @Value("${sms.sender-id:}") String senderId,
                      @Value("${sms.endpoint:https://api.africastalking.com/version1/messaging}") String endpoint) {
        this.restTemplate = restTemplate;
        this.username = username;
        this.apiKey = apiKey;
        this.senderId = senderId;
        this.endpoint = endpoint;
        this.configured = StringUtils.hasText(username) && StringUtils.hasText(apiKey);
        if (!configured) {
            log.warn("sms.api-key is not set — SMS notifications will be logged instead of sent. "
                    + "Set SMS_USERNAME/SMS_API_KEY to enable real delivery.");
        }
    }

    @Override
    public Channel type() { return Channel.SMS; }

    @Override
    public void deliver(Notification n) throws DeliveryException {
        String to = normalise(n.getRecipientPhone());
        if (to == null) {
            throw new DeliveryException("No usable recipient phone for notification " + n.getId());
        }

        String text = n.getBody() == null ? "" : n.getBody().trim();
        if (text.length() > SINGLE_SEGMENT) {
            log.warn("SMS body for template {} is {} characters and will be billed as multiple parts",
                    n.getTemplateCode(), text.length());
        }

        if (!configured) {
            log.info("SMS (not sent, no provider configured) to={} \n{}", to, text);
            return;
        }

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
            headers.setAccept(java.util.List.of(MediaType.APPLICATION_JSON));
            headers.set("apiKey", apiKey);

            MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
            form.add("username", username);
            form.add("to", to);
            form.add("message", text);
            if (StringUtils.hasText(senderId)) form.add("from", senderId);

            ResponseEntity<String> res =
                    restTemplate.postForEntity(endpoint, new HttpEntity<>(form, headers), String.class);

            // Africa's Talking answers 201 on accept. Anything else is worth retrying,
            // which is what throwing here gets us — the store records the attempt and the
            // retry job picks it up.
            if (!res.getStatusCode().is2xxSuccessful()) {
                throw new DeliveryException("SMS provider returned " + res.getStatusCode());
            }
            log.info("SMS sent to {} for template {}", to, n.getTemplateCode());
        } catch (DeliveryException e) {
            throw e;
        } catch (Exception e) {
            throw new DeliveryException("SMS send failed: " + e.getMessage(), e);
        }
    }

    /**
     * Kenyan numbers reach us as 0712…, 254712… or +254712… depending on where they were
     * typed. The provider requires E.164, and a number rejected at the gateway costs a
     * retry cycle to discover, so the normalising happens here rather than being assumed
     * of every caller.
     */
    static String normalise(String raw) {
        if (!StringUtils.hasText(raw)) return null;
        String digits = raw.replaceAll("[^0-9+]", "");
        if (digits.startsWith("+")) return digits.length() > 6 ? digits : null;
        if (digits.startsWith("0")) return "+254" + digits.substring(1);
        if (digits.startsWith("254")) return "+" + digits;
        return digits.length() > 6 ? "+" + digits : null;
    }
}
