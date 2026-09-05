package com.smartseason.notification.dispatch;

import java.util.HashMap;
import java.util.Map;

public class TemplateSet {

    public record Entry(String subject, String body) {
    }

    private final Map<String, Entry> byLocale = new HashMap<>();

    public TemplateSet put(String locale, String subject, String body) {
        byLocale.put(locale, new Entry(subject, body));
        return this;
    }

    public Entry forLocale(String locale) {
        return byLocale.get(locale);
    }

    public boolean isEmpty() {
        return byLocale.isEmpty();
    }
}
