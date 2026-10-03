package com.hms.notify;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * The wording of every message, in one place. A template receives only the parameters the outbox row holds, and none of them is a
 * secret: the portal invitation code is given to the patient in person and is never part of a message.
 */
final class NotificationTemplates {
    private NotificationTemplates() {}

    record Rendered(String subject, String body) {}

    static Rendered render(String template, JsonNode params) {
        String organisation = text(params, "organisation", "your health facility");
        String name = text(params, "givenName", "");
        String hello = name.isBlank() ? "Hello," : "Hello " + name + ",";
        return switch (template) {
            case "PORTAL_INVITED" -> new Rendered(organisation + ": your patient portal invitation",
                    hello + " " + organisation + " has set up a patient portal invitation for you. Staff will give you a one-time code in person"
                            + (params.hasNonNull("expiresOn") ? ", valid until " + params.get("expiresOn").asText() : "")
                            + ". You will also need your date of birth. We never ask for this code by phone, message or e-mail.");
            case "PORTAL_ACTIVATED" -> new Rendered(organisation + ": your patient portal account was created",
                    hello + " a patient portal account was just created for you at " + organisation
                            + ". If this was not you, tell the facility straight away so it can be disabled.");
            default -> throw new IllegalArgumentException("unknown notification template " + template);
        };
    }

    private static String text(JsonNode params, String key, String fallback) {
        return params != null && params.hasNonNull(key) ? params.get(key).asText() : fallback;
    }
}
