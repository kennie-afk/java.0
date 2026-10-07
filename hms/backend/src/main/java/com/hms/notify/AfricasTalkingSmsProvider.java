package com.hms.notify;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hms.registry.Phones;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Set;

/**
 * SMS through Africa's Talking (POST {base}/version1/messaging, form-encoded, apiKey header). WRITTEN FROM THE PUBLIC DOCUMENTATION AND NEVER
 * RUN AGAINST AFRICA'S TALKING: tested only against a stub that imitates the documented response. The sandbox base URL is
 * https://api.sandbox.africastalking.com; check the field names and status codes against the current documentation before relying on it.
 */
final class AfricasTalkingSmsProvider implements NotificationProvider {

    /** Per-recipient status codes that mean the number or sender can never work, so retrying only wastes attempts. */
    private static final Set<Integer> PERMANENT = Set.of(401, 402, 403, 404, 406);

    private final String baseUrl;
    private final String username;
    private final String apiKey;
    private final String senderId;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private final ObjectMapper json = new ObjectMapper();

    AfricasTalkingSmsProvider(String baseUrl, String username, String apiKey, String senderId) {
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.username = username;
        this.apiKey = apiKey;
        this.senderId = senderId;
    }

    @Override
    public String channel() {
        return "SMS";
    }

    @Override
    public boolean isMock() {
        return false;
    }

    @Override
    public void send(Outbound m) throws DeliveryException {
        String to;
        try {
            to = Phones.normalise(m.recipient());
        } catch (IllegalArgumentException e) {
            throw new DeliveryException("The recipient is not a Kenyan mobile number.", true);
        }
        if (to == null) {
            throw new DeliveryException("The recipient is empty.", true);
        }
        StringBuilder form = new StringBuilder("username=").append(enc(username)).append("&to=").append(enc(to)).append("&message=").append(enc(m.body()));
        if (senderId != null && !senderId.isBlank()) {
            form.append("&from=").append(enc(senderId));
        }
        HttpResponse<String> r;
        try {
            r = http.send(HttpRequest.newBuilder(URI.create(baseUrl + "/version1/messaging")).timeout(Duration.ofSeconds(20))
                    .header("apiKey", apiKey).header("Accept", "application/json").header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(form.toString())).build(), HttpResponse.BodyHandlers.ofString());
        } catch (IOException e) {
            throw new DeliveryException("Africa's Talking could not be reached.", false);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new DeliveryException("Interrupted while sending.", false);
        }
        int http = r.statusCode();
        if (http == 400 || http == 401 || http == 403) {
            // The account or the request is wrong: retrying the same thing cannot help.
            throw new DeliveryException("Africa's Talking refused the request (HTTP " + http + "); check the username and API key.", true);
        }
        if (http / 100 != 2) {
            throw new DeliveryException("Africa's Talking answered HTTP " + http + ".", false);
        }
        JsonNode recipients;
        try {
            recipients = json.readTree(r.body()).path("SMSMessageData").path("Recipients");
        } catch (IOException e) {
            throw new DeliveryException("Africa's Talking sent an answer that could not be read.", false);
        }
        if (!recipients.isArray() || recipients.isEmpty()) {
            throw new DeliveryException("Africa's Talking accepted no recipient.", false);
        }
        int code = recipients.get(0).path("statusCode").asInt(-1);
        // 100 Processed, 101 Sent, 102 Queued.
        if (code >= 100 && code <= 102) {
            return;
        }
        throw new DeliveryException("Africa's Talking status " + code + " (" + recipients.get(0).path("status").asText("unknown") + ").", PERMANENT.contains(code));
    }

    private static String enc(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }
}
